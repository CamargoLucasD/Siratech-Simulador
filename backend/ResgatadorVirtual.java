package backend;

/**
 * Resgatador virtual — agente autônomo que se move até o animal
 * fora da área e o acompanha de volta ao centro da fazenda.
 */
public class ResgatadorVirtual {

    public enum Fase { INDO, RETORNANDO }

    private static final double VELOCIDADE = 4.5; // pixels por tick
    private static final double RAIO_CHEGADA = 18.0; // distância para considerar "chegou"

    private final String nome;
    private double x;
    private double y;
    private Fase fase;
    private final AnimalSimulado alvo;

    // Posição destino de retorno (centro do mapa / curral)
    private final double destinoX;
    private final double destinoY;

    // Flag de conclusão — sinaliza ao engine que o resgate terminou
    private boolean concluido = false;

    // ── Construtor ─────────────────────────────────────────────────────────────

    /**
     * @param nome      Nome exibido no mapa e no log
     * @param inicioX   Posição inicial X do resgatador (ex: portão do curral)
     * @param inicioY   Posição inicial Y do resgatador
     * @param alvo      Animal a ser resgatado
     * @param destinoX  X de destino final (centro da área segura)
     * @param destinoY  Y de destino final
     */
    public ResgatadorVirtual(String nome, double inicioX, double inicioY,
                              AnimalSimulado alvo, double destinoX, double destinoY) {
        this.nome     = nome;
        this.x        = inicioX;
        this.y        = inicioY;
        this.alvo     = alvo;
        this.fase     = Fase.INDO;
        this.destinoX = destinoX;
        this.destinoY = destinoY;
    }

    // ── Loop de atualização ────────────────────────────────────────────────────

    /**
     * Avança o resgatador um tick.
     * Retorna true quando o resgate estiver completamente concluído.
     */
    public boolean tick() {
        if (concluido) return true;

        switch (fase) {
            case INDO -> {
                // Move em direção ao animal
                double tx = alvo.getX();
                double ty = alvo.getY();
                if (moverPara(tx, ty)) {
                    // Chegou ao animal → começa a retornar com ele
                    fase = Fase.RETORNANDO;
                    alvo.setEstado(EstadoAnimal.RETORNANDO);
                }
            }
            case RETORNANDO -> {
                // Move em direção ao destino (área segura)
                if (moverPara(destinoX, destinoY)) {
                    // Chegou à área segura → resgate concluído
                    concluido = true;
                    alvo.setEstado(EstadoAnimal.ANDANDO);
                    alvo.resetarEstadoFora();
                    alvo.setX(destinoX + (Math.random() - 0.5) * 60);
                    alvo.setY(destinoY + (Math.random() - 0.5) * 60);
                    return true;
                }
                // Arrasta o animal junto durante o retorno
                alvo.setX(x);
                alvo.setY(y);
            }
        }
        return false;
    }

    /**
     * Move o resgatador na direção de (tx, ty).
     * Retorna true se chegou (distância ≤ RAIO_CHEGADA).
     */
    private boolean moverPara(double tx, double ty) {
        double dx = tx - x;
        double dy = ty - y;
        double dist = Math.hypot(dx, dy);
        if (dist <= RAIO_CHEGADA) return true;

        double fator = VELOCIDADE / dist;
        x += dx * fator;
        y += dy * fator;
        return false;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String        getNome()     { return nome; }
    public double        getX()        { return x; }
    public double        getY()        { return y; }
    public Fase          getFase()     { return fase; }
    public AnimalSimulado getAlvo()   { return alvo; }
    public boolean       isConcluido(){ return concluido; }

    /** Distância atual entre o resgatador e o animal. */
    public double getDistanciaAlvo() {
        return Math.hypot(alvo.getX() - x, alvo.getY() - y);
    }

    @Override
    public String toString() {
        return "ResgatadorVirtual{nome=" + nome + ", fase=" + fase
            + ", alvo=" + alvo.getAnimal().getId() + "}";
    }
}
