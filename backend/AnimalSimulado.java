package backend;

/**
 * Encapsula um Animal real do banco de dados, adicionando
 * estado, posição e comportamento necessários para a simulação.
 */
public class AnimalSimulado {

    private final Animal animal;

    // Posição em pixels no mapa (coordenadas lógicas do MapaPanel)
    private double x;
    private double y;

    // Velocidade em pixels por tick
    private double vx;
    private double vy;

    // Coordenadas GPS simuladas
    private double latitude;
    private double longitude;

    // Necessidades vitais (0–100)
    private double energia  = 80.0;
    private double fome     = 20.0;
    private double sede     = 20.0;

    // Estado comportamental atual
    private EstadoAnimal estado = EstadoAnimal.ANDANDO;

    // Quantos ticks o animal está fora da área
    private int ticksForaDaArea = 0;

    // Fase do pisca-pisca (usado pelo MapaPanel para animar alertas)
    private double blinkPhase = 0.0;

    // true se o alerta já foi gerado no banco para este episódio de saída
    private boolean alertaGerado = false;

    // Nome do resgatador designado (null = sem resgate em andamento)
    private String resgatadorNome = null;

    // ── Construtor ─────────────────────────────────────────────────────────────

    public AnimalSimulado(Animal animal, double x, double y) {
        this.animal = animal;
        this.x = x;
        this.y = y;
        // Velocidade inicial aleatória pequena
        this.vx = (Math.random() - 0.5) * 2.0;
        this.vy = (Math.random() - 0.5) * 2.0;
    }

    // ── Getters e setters ─────────────────────────────────────────────────────

    public Animal getAnimal()   { return animal; }

    public double getX()        { return x; }
    public void   setX(double x){ this.x = x; }

    public double getY()        { return y; }
    public void   setY(double y){ this.y = y; }

    public double getVx()         { return vx; }
    public void   setVx(double vx){ this.vx = vx; }

    public double getVy()         { return vy; }
    public void   setVy(double vy){ this.vy = vy; }

    public double getLatitude()             { return latitude; }
    public void   setLatitude(double lat)   { this.latitude = lat; }

    public double getLongitude()            { return longitude; }
    public void   setLongitude(double lon)  { this.longitude = lon; }

    public double getEnergia()              { return energia; }
    public void   setEnergia(double v)      { this.energia = clamp(v); }

    public double getFome()                 { return fome; }
    public void   setFome(double v)         { this.fome = clamp(v); }

    public double getSede()                 { return sede; }
    public void   setSede(double v)         { this.sede = clamp(v); }

    public EstadoAnimal getEstado()                 { return estado; }
    public void         setEstado(EstadoAnimal est) { this.estado = est; }

    public int  getTicksForaDaArea()        { return ticksForaDaArea; }
    public void setTicksForaDaArea(int t)   { this.ticksForaDaArea = t; }
    public void incrementarTicksForaDaArea(){ this.ticksForaDaArea++; }

    public double getBlinkPhase()           { return blinkPhase; }
    public void   setBlinkPhase(double v)   { this.blinkPhase = v; }
    public void   avancarBlinkPhase(double delta) {
        this.blinkPhase = (this.blinkPhase + delta) % (Math.PI * 2);
    }

    public boolean isAlertaGerado()         { return alertaGerado; }
    public void    setAlertaGerado(boolean v){ this.alertaGerado = v; }

    public String getResgatadorNome()           { return resgatadorNome; }
    public void   setResgatadorNome(String nome){ this.resgatadorNome = nome; }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Garante que um valor de necessidade fique entre 0 e 100. */
    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    /** Reseta o estado de fora-da-área para um novo ciclo. */
    public void resetarEstadoFora() {
        this.ticksForaDaArea = 0;
        this.alertaGerado    = false;
        this.resgatadorNome  = null;
    }

    @Override
    public String toString() {
        return "AnimalSimulado{id=" + animal.getId()
            + ", nome=" + animal.getNome()
            + ", estado=" + estado
            + ", x=" + String.format("%.1f", x)
            + ", y=" + String.format("%.1f", y)
            + "}";
    }
}
