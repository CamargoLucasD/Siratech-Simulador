package backend;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TotemCaptura — ponto fixo no mapa que detecta animais que passam
 * dentro do seu raio de alcance (funciona como uma AirTag estacionária).
 *
 * Cada totem tem:
 *   - Posição fixa no mapa (x, y em pixels)
 *   - Raio de detecção (em pixels)
 *   - Histórico de capturas (qual animal passou e quando)
 *
 * A detecção é feita pelo SimulacaoEngine a cada tick.
 */
public class TotemCaptura {

    // ── Identificação ──────────────────────────────────────────────────────────
    private final int    id;
    private final String nome;

    // ── Posição no mapa (pixels) ───────────────────────────────────────────────
    private double x;
    private double y;

    // ── Raio de detecção (pixels) ─────────────────────────────────────────────
    private final double raioDeteccao;

    // ── Histórico de capturas ─────────────────────────────────────────────────
    private final List<RegistroCaptura> historico = new ArrayList<>();

    // ── Controle de animais já "dentro" para não logar toda tick ─────────────
    // Guarda IDs de animais que JÁ estão no raio neste momento
    private final java.util.Set<Integer> animaisDentroAgora =
        new java.util.HashSet<>();

    // ── Callback disparado quando um animal é capturado ───────────────────────
    private java.util.function.BiConsumer<TotemCaptura, AnimalSimulado> onCaptura;

    // ── Animação: pulso visual (controlado pelo MapaPanel) ────────────────────
    private double pulsoFase = 0.0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Construtor
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @param id           Identificador único do totem
     * @param nome         Nome descritivo (ex: "Totem Norte", "Totem Curral")
     * @param x            Posição X no mapa (pixels)
     * @param y            Posição Y no mapa (pixels)
     * @param raioDeteccao Raio de alcance em pixels (ex: 80 = ~80px de raio)
     */
    public TotemCaptura(int id, String nome, double x, double y, double raioDeteccao) {
        this.id           = id;
        this.nome         = nome;
        this.x            = x;
        this.y            = y;
        this.raioDeteccao = raioDeteccao;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Detecção — chamada pelo SimulacaoEngine a cada tick
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Verifica se o animal está dentro do raio de detecção.
     * Registra a captura apenas na ENTRADA (borda de subida), não a cada tick.
     *
     * @param animal Animal a verificar
     * @return true se capturou (nova entrada no raio)
     */
    public boolean verificar(AnimalSimulado animal) {
        int animalId = animal.getAnimal().getId();
        double dist  = Math.hypot(animal.getX() - x, animal.getY() - y);
        boolean dentroAgora = dist <= raioDeteccao;

        if (dentroAgora) {
            if (!animaisDentroAgora.contains(animalId)) {
                // Nova entrada — registra captura
                animaisDentroAgora.add(animalId);
                RegistroCaptura reg = new RegistroCaptura(animal, dist);
                historico.add(reg);

                if (onCaptura != null) {
                    onCaptura.accept(this, animal);
                }

                System.out.printf("[Totem #%d – %s] Animal #%d (%s) detectado a %.0f px%n",
                    id, nome, animalId,
                    animal.getAnimal().getNome() != null ? animal.getAnimal().getNome() : "?",
                    dist);
                return true;
            }
        } else {
            // Saiu do raio — remove do controle para permitir nova captura na próxima entrada
            animaisDentroAgora.remove(animalId);
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Animação
    // ═══════════════════════════════════════════════════════════════════════════

    /** Avança a fase de pulso visual (chamado pelo MapaPanel no repaint). */
    public void avancarPulso(double delta) {
        pulsoFase = (pulsoFase + delta) % (Math.PI * 2);
    }

    /** Valor 0..1 para controlar a opacidade do anel de pulso. */
    public double getPulsoAlpha() {
        return 0.4 + 0.35 * Math.sin(pulsoFase);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Getters
    // ═══════════════════════════════════════════════════════════════════════════

    public int    getId()           { return id; }
    public String getNome()         { return nome; }
    public double getX()            { return x; }
    public double getY()            { return y; }
    public void   setX(double x)    { this.x = x; }
    public void   setY(double y)    { this.y = y; }
    public double getRaioDeteccao() { return raioDeteccao; }

    public List<RegistroCaptura> getHistorico() {
        return java.util.Collections.unmodifiableList(historico);
    }

    /** Quantos animais diferentes já passaram por este totem. */
    public int totalCapturas() { return historico.size(); }

    /** Quantos animais estão dentro do raio agora. */
    public int quantidadeDentroAgora() { return animaisDentroAgora.size(); }

    public void setOnCaptura(
            java.util.function.BiConsumer<TotemCaptura, AnimalSimulado> cb) {
        this.onCaptura = cb;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Registro de captura (classe interna)
    // ═══════════════════════════════════════════════════════════════════════════

    public static class RegistroCaptura {
        private final AnimalSimulado animal;
        private final LocalDateTime  timestamp;
        private final double         distancia;

        RegistroCaptura(AnimalSimulado animal, double distancia) {
            this.animal    = animal;
            this.timestamp = LocalDateTime.now();
            this.distancia = distancia;
        }

        public AnimalSimulado getAnimal()    { return animal; }
        public LocalDateTime  getTimestamp() { return timestamp; }
        public double         getDistancia() { return distancia; }

        public String getTimestampFormatado() {
            return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        @Override
        public String toString() {
            String nomeAnimal = animal.getAnimal().getNome();
            return String.format("[%s] Animal #%d (%s) — %.0fpx",
                getTimestampFormatado(),
                animal.getAnimal().getId(),
                nomeAnimal != null ? nomeAnimal : "?",
                distancia);
        }
    }
}
