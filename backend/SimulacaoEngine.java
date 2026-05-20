package backend;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * SimulacaoEngine — coração do simulador.
 *
 * Correções aplicadas:
 *   - Bug do congelamento: animais EM_RESGATE/RETORNANDO continuam sendo
 *     processados (blink + GPS) mas o ResgatadorVirtual.tick() é quem move
 *     o animal. O mapa não congela porque o timer do engine continua rodando
 *     e o onTick dispara normalmente.
 *   - Suporte a trocarFazenda() em runtime: para o timer, recarrega animais
 *     na nova fazenda e reinicia o timer.
 */
public class SimulacaoEngine {

    // ── Dimensões do mapa lógico (deve coincidir com MapaPanel.MAP_W/H) ─────────
    private static final int MAP_W = 1600;
    private static final int MAP_H = 1200;

    // ── Área segura (geofence em pixels) ─────────────────────────────────────────
    private static final int AREA_X1 = 200;
    private static final int AREA_Y1 = 200;
    private static final int AREA_X2 = 1400;
    private static final int AREA_Y2 = 1000;

    // ── Centro da área (ponto de retorno dos resgates) ────────────────────────────
    private static final double CENTRO_X = (AREA_X1 + AREA_X2) / 2.0; // 800
    private static final double CENTRO_Y = (AREA_Y1 + AREA_Y2) / 2.0; // 600

    // ── Posições de interesse no mapa (em pixels) ─────────────────────────────────
    private static final double LAGO_X   = 320, LAGO_Y   = 640;
    private static final double ALIM_X   = 780, ALIM_Y   = 760;
    private static final double CURRAL_X = 690, CURRAL_Y = 220;

    // ── Frequência do timer ───────────────────────────────────────────────────────
    private int intervaloMs = 100;
    private static final int TICKS_REGISTRO_GPS = 30;

    // ── Estado da simulação ───────────────────────────────────────────────────────
    private final List<AnimalSimulado>    animais      = new CopyOnWriteArrayList<>();
    private final List<ResgatadorVirtual> resgatadores = new CopyOnWriteArrayList<>();
    private final SimulacaoService simulacaoService;
    private Fazenda fazenda; // não-final: pode ser trocada via trocarFazenda()

    private Timer   timer;
    private boolean pausado = false;
    private long    tick    = 0L;
    private final Random rng = new Random();

    // ── Callbacks para o frontend ─────────────────────────────────────────────────
    private Consumer<Long>           onTick;
    private Consumer<AnimalSimulado> onSaida;
    private Consumer<AnimalSimulado> onResgate;
    private Consumer<AnimalSimulado> onResgateCompleto;
    /** Disparado quando a fazenda é trocada com sucesso. Argumento: nova fazenda. */
    private Consumer<Fazenda>        onFazendaTrocada;

    // ── Contadores de mudança de direção por animal ───────────────────────────────
    private final java.util.Map<Integer, Integer> ticksMudancaDirecao
        = new java.util.concurrent.ConcurrentHashMap<>();

    // ── GPS: referência da fazenda → pixels ──────────────────────────────────────
    private double latCentro;
    private double lonCentro;
    private double raioMetros;
    private double metrosPorPixelX;
    private double metrosPorPixelY;

    // ── Contador para registro periódico no banco ─────────────────────────────────
    private int contadorRegistro = 0;

    // ═══════════════════════════════════════════════════════════════════════════════
    // Construtor
    // ═══════════════════════════════════════════════════════════════════════════════
    public SimulacaoEngine(SimulacaoService simulacaoService, Fazenda fazenda) {
        this.simulacaoService = simulacaoService;
        this.fazenda          = fazenda;
        recalcularEscalaGPS();
    }

    /** Recalcula os parâmetros de conversão GPS↔pixels com base em this.fazenda. */
    private void recalcularEscalaGPS() {
        this.latCentro  = fazenda.getLatitudeCentro();
        this.lonCentro  = fazenda.getLongitudeCentro();
        this.raioMetros = fazenda.getRaioMetros() > 0 ? fazenda.getRaioMetros() : 500.0;

        double areaPixelW = (AREA_X2 - AREA_X1) / 2.0;
        double areaPixelH = (AREA_Y2 - AREA_Y1) / 2.0;
        this.metrosPorPixelX = raioMetros / areaPixelW;
        this.metrosPorPixelY = raioMetros / areaPixelH;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Carregamento de animais
    // ═══════════════════════════════════════════════════════════════════════════════

    public void carregarAnimais(List<Animal> animaisDB) {
        animais.clear();
        ticksMudancaDirecao.clear();
        for (Animal a : animaisDB) {
            double x = AREA_X1 + 80 + rng.nextDouble() * (AREA_X2 - AREA_X1 - 160);
            double y = AREA_Y1 + 80 + rng.nextDouble() * (AREA_Y2 - AREA_Y1 - 160);
            AnimalSimulado sim = new AnimalSimulado(a, x, y);
            sim.setLatitude(pixelParaLat(y));
            sim.setLongitude(pixelParaLon(x));
            animais.add(sim);
        }
        System.out.println("[SimulacaoEngine] " + animais.size() + " animais carregados.");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Controle
    // ═══════════════════════════════════════════════════════════════════════════════

    public void iniciar() {
        if (timer != null && timer.isRunning()) return;
        timer = new Timer(intervaloMs, e -> executarTick());
        timer.start();
        System.out.println("[SimulacaoEngine] Simulação iniciada.");
    }

    public void pausar() {
        pausado = true;
        System.out.println("[SimulacaoEngine] Simulação pausada.");
    }

    public void retomar() {
        pausado = false;
        System.out.println("[SimulacaoEngine] Simulação retomada.");
    }

    public void resetar() {
        if (timer != null) timer.stop();
        timer   = null;
        pausado = false;
        tick    = 0L;
        resgatadores.clear();
        ticksMudancaDirecao.clear();
        for (AnimalSimulado a : animais) {
            double x = AREA_X1 + 80 + rng.nextDouble() * (AREA_X2 - AREA_X1 - 160);
            double y = AREA_Y1 + 80 + rng.nextDouble() * (AREA_Y2 - AREA_Y1 - 160);
            a.setX(x);
            a.setY(y);
            a.setVx((rng.nextDouble() - 0.5) * 2.0);
            a.setVy((rng.nextDouble() - 0.5) * 2.0);
            a.setEstado(EstadoAnimal.ANDANDO);
            a.setEnergia(80.0);
            a.setFome(20.0);
            a.setSede(20.0);
            a.resetarEstadoFora();
            a.setLatitude(pixelParaLat(y));
            a.setLongitude(pixelParaLon(x));
        }
        System.out.println("[SimulacaoEngine] Simulação reiniciada.");
    }

    public boolean isPausado() { return pausado; }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Troca de fazenda em runtime
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Para o engine, troca a fazenda e recarrega os animais vinculados a ela.
     * Após este método o engine fica parado — o frontend deve chamar iniciar()
     * se quiser retomar a simulação imediatamente.
     *
     * @param novaFazenda   Fazenda selecionada
     * @param animaisNovos  Animais do banco já filtrados pela nova fazenda_id
     */
    public void trocarFazenda(Fazenda novaFazenda, List<Animal> animaisNovos) {
        // 1. Para tudo
        boolean estavaRodando = (timer != null && timer.isRunning() && !pausado);
        if (timer != null) timer.stop();
        timer    = null;
        pausado  = false;
        tick     = 0L;
        resgatadores.clear();
        ticksMudancaDirecao.clear();

        // 2. Troca a fazenda e recalcula escala GPS
        this.fazenda = novaFazenda;
        recalcularEscalaGPS();

        // 3. Recarrega animais
        carregarAnimais(animaisNovos);

        System.out.println("[SimulacaoEngine] Fazenda trocada para: " + novaFazenda.getNome()
            + " | " + animaisNovos.size() + " animais.");

        // 4. Notifica o frontend
        if (onFazendaTrocada != null) onFazendaTrocada.accept(novaFazenda);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Resgate
    // ═══════════════════════════════════════════════════════════════════════════════

    public void iniciarResgate(AnimalSimulado alvo) {
        if (alvo.getEstado() != EstadoAnimal.FORA_DA_AREA) return;
        if (alvo.getResgatadorNome() != null) return;

        String[] nomes = {"Carlos", "Ana", "Pedro", "Joana", "Roberto", "Marta"};
        String nome = nomes[rng.nextInt(nomes.length)];

        ResgatadorVirtual r = new ResgatadorVirtual(
            nome, CURRAL_X, CURRAL_Y, alvo, CENTRO_X, CENTRO_Y);

        alvo.setResgatadorNome(nome);
        alvo.setEstado(EstadoAnimal.EM_RESGATE);
        resgatadores.add(r);

        if (onResgate != null) onResgate.accept(alvo);
        System.out.println("[SimulacaoEngine] Resgate iniciado: " + nome
            + " → animal #" + alvo.getAnimal().getId());
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Loop principal
    // ═══════════════════════════════════════════════════════════════════════════════

    private void executarTick() {
        if (pausado) return;
        tick++;
        contadorRegistro++;

        // ── Atualiza animais ──────────────────────────────────────────────────────
        for (AnimalSimulado a : animais) {
            atualizarAnimal(a);
        }

        // ── Atualiza resgatadores ─────────────────────────────────────────────────
        Iterator<ResgatadorVirtual> it = resgatadores.iterator();
        while (it.hasNext()) {
            ResgatadorVirtual r = it.next();
            boolean concluiu = r.tick();
            if (concluiu) {
                it.remove();
                AnimalSimulado alvo = r.getAlvo();
                simulacaoService.resolverAlertaRetorno(alvo);
                if (onResgateCompleto != null) onResgateCompleto.accept(alvo);
                System.out.println("[SimulacaoEngine] Resgate concluído: animal #"
                    + alvo.getAnimal().getId());
            }
        }

        // ── Registro periódico no banco ───────────────────────────────────────────
        if (contadorRegistro >= TICKS_REGISTRO_GPS) {
            contadorRegistro = 0;
            for (AnimalSimulado a : animais) {
                simulacaoService.registrarPosicao(a);
            }
        }

        // ── Notifica o frontend ───────────────────────────────────────────────────
        if (onTick != null) onTick.accept(tick);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Comportamento dos animais
    // ═══════════════════════════════════════════════════════════════════════════════

    private void atualizarAnimal(AnimalSimulado a) {

        // ── FIX DO CONGELAMENTO ───────────────────────────────────────────────────
        // Animais em resgate/retornando têm posição controlada pelo ResgatadorVirtual
        // (via r.tick() → alvo.setX/setY). Aqui apenas atualizamos o blink e o GPS,
        // sem retornar early — o resto do método é pulado pelo switch/if abaixo.
        if (a.getEstado() == EstadoAnimal.EM_RESGATE
         || a.getEstado() == EstadoAnimal.RETORNANDO) {
            a.avancarBlinkPhase(0.15);
            atualizarGPS(a);
            return; // posição já foi atualizada pelo ResgatadorVirtual.tick()
        }

        // ── Desgaste metabólico ───────────────────────────────────────────────────
        a.setFome(a.getFome()       + 0.04);
        a.setSede(a.getSede()       + 0.06);
        a.setEnergia(a.getEnergia() - 0.02);

        // ── Seleção de comportamento ──────────────────────────────────────────────
        EstadoAnimal novoEstado = selecionarComportamento(a);
        if (novoEstado != a.getEstado() && a.getEstado() != EstadoAnimal.FORA_DA_AREA) {
            a.setEstado(novoEstado);
        }

        // ── Movimento baseado no estado ───────────────────────────────────────────
        switch (a.getEstado()) {
            case BEBENDO     -> moverPara(a, LAGO_X, LAGO_Y, 2.5);
            case COMENDO     -> moverPara(a, ALIM_X, ALIM_Y, 2.0);
            case DESCANSANDO -> {
                a.setVx(a.getVx() * 0.85);
                a.setVy(a.getVy() * 0.85);
                a.setEnergia(a.getEnergia() + 0.15);
            }
            case FORA_DA_AREA -> {
                moverLivre(a);
                a.incrementarTicksForaDaArea();
                a.avancarBlinkPhase(0.25);
                if (!a.isAlertaGerado()) {
                    a.setAlertaGerado(true);
                    simulacaoService.gerarAlertaForaDaArea(a);
                    if (onSaida != null) onSaida.accept(a);
                }
            }
            default -> moverLivre(a);
        }

        // ── Efeitos de recursos ───────────────────────────────────────────────────
        if (a.getEstado() == EstadoAnimal.BEBENDO && distancia(a, LAGO_X, LAGO_Y) < 60) {
            a.setSede(a.getSede() - 0.5);
            if (a.getSede() <= 5) a.setEstado(EstadoAnimal.ANDANDO);
        }
        if (a.getEstado() == EstadoAnimal.COMENDO && distancia(a, ALIM_X, ALIM_Y) < 60) {
            a.setFome(a.getFome() - 0.4);
            if (a.getFome() <= 5) a.setEstado(EstadoAnimal.ANDANDO);
        }

        // ── Aplicar velocidade ────────────────────────────────────────────────────
        a.setX(a.getX() + a.getVx());
        a.setY(a.getY() + a.getVy());

        // ── Verificar limite da área ──────────────────────────────────────────────
        boolean estaFora = a.getX() < AREA_X1 || a.getX() > AREA_X2
                        || a.getY() < AREA_Y1 || a.getY() > AREA_Y2;

        if (estaFora && a.getEstado() != EstadoAnimal.FORA_DA_AREA) {
            a.setEstado(EstadoAnimal.FORA_DA_AREA);
        } else if (!estaFora && a.getEstado() == EstadoAnimal.FORA_DA_AREA
                && a.getResgatadorNome() == null) {
            // Voltou sozinho
            a.setEstado(EstadoAnimal.ANDANDO);
            simulacaoService.resolverAlertaRetorno(a);
            a.resetarEstadoFora();
        }

        // ── Limitar ao mapa total ─────────────────────────────────────────────────
        if (a.getX() < 0)     { a.setX(0);     a.setVx( Math.abs(a.getVx())); }
        if (a.getX() > MAP_W) { a.setX(MAP_W); a.setVx(-Math.abs(a.getVx())); }
        if (a.getY() < 0)     { a.setY(0);     a.setVy( Math.abs(a.getVy())); }
        if (a.getY() > MAP_H) { a.setY(MAP_H); a.setVy(-Math.abs(a.getVy())); }

        atualizarGPS(a);
    }

    private EstadoAnimal selecionarComportamento(AnimalSimulado a) {
        if (a.getEstado() == EstadoAnimal.FORA_DA_AREA) return EstadoAnimal.FORA_DA_AREA;
        if (a.getSede()    > 75) return EstadoAnimal.BEBENDO;
        if (a.getFome()    > 75) return EstadoAnimal.COMENDO;
        if (a.getEnergia() < 25) return EstadoAnimal.DESCANSANDO;
        if (rng.nextDouble() < 0.002) return EstadoAnimal.EM_GRUPO;
        if (a.getEstado() == EstadoAnimal.ANDANDO
         || a.getEstado() == EstadoAnimal.EM_GRUPO) return a.getEstado();
        return EstadoAnimal.ANDANDO;
    }

    private void moverPara(AnimalSimulado a, double tx, double ty, double vel) {
        double dx = tx - a.getX(), dy = ty - a.getY();
        double dist = Math.hypot(dx, dy);
        if (dist < 5) return;
        a.setVx(dx / dist * vel);
        a.setVy(dy / dist * vel);
    }

    private void moverLivre(AnimalSimulado a) {
        int id = a.getAnimal().getId();
        int ticksAte = ticksMudancaDirecao.getOrDefault(id, 0) - 1;
        if (ticksAte <= 0) {
            double angulo = rng.nextDouble() * Math.PI * 2;
            double vel    = 0.8 + rng.nextDouble() * 1.8;
            a.setVx(Math.cos(angulo) * vel);
            a.setVy(Math.sin(angulo) * vel);
            ticksMudancaDirecao.put(id, 30 + rng.nextInt(60));
        } else {
            ticksMudancaDirecao.put(id, ticksAte);
        }
        a.setVx(a.getVx() * 0.98);
        a.setVy(a.getVy() * 0.98);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Conversão GPS ↔ pixels
    // ═══════════════════════════════════════════════════════════════════════════════

    private void atualizarGPS(AnimalSimulado a) {
        a.setLatitude(pixelParaLat(a.getY()));
        a.setLongitude(pixelParaLon(a.getX()));
    }

    private double pixelParaLat(double y) {
        double offsetMetros = (CENTRO_Y - y) * metrosPorPixelY;
        return latCentro + offsetMetros / 111320.0;
    }

    private double pixelParaLon(double x) {
        double offsetMetros = (x - CENTRO_X) * metrosPorPixelX;
        double cosLat = Math.cos(Math.toRadians(latCentro));
        return lonCentro + offsetMetros / (111320.0 * cosLat);
    }

    private double distancia(AnimalSimulado a, double tx, double ty) {
        return Math.hypot(a.getX() - tx, a.getY() - ty);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Getters / Setters
    // ═══════════════════════════════════════════════════════════════════════════════

    public List<AnimalSimulado>    getAnimais()      { return animais; }
    public List<ResgatadorVirtual> getResgatadores() { return resgatadores; }
    public long                    getTick()         { return tick; }
    public int                     getIntervaloMs()  { return intervaloMs; }
    public Fazenda                 getFazenda()      { return fazenda; }

    public void setIntervaloMs(int ms) {
        this.intervaloMs = ms;
        if (timer != null && timer.isRunning()) timer.setDelay(ms);
    }

    public void setOnTick(Consumer<Long> cb)                       { this.onTick = cb; }
    public void setOnSaida(Consumer<AnimalSimulado> cb)            { this.onSaida = cb; }
    public void setOnResgate(Consumer<AnimalSimulado> cb)          { this.onResgate = cb; }
    public void setOnResgateCompleto(Consumer<AnimalSimulado> cb)  { this.onResgateCompleto = cb; }
    public void setOnFazendaTrocada(Consumer<Fazenda> cb)          { this.onFazendaTrocada = cb; }
}
