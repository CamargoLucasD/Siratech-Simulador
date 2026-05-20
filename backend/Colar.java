package backend;

import jakarta.persistence.*;

@Entity
@Table(name = "colares")
public class Colar {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "bateria")
    private int bateria;

    @Column(name = "nivel_sinal")
    private String nivelSinal;

    @Column(name = "frequencia_minutos")
    private int frequenciaMinutos;

    @Column(name = "firmware")
    private String firmware;

    @Column(name = "disponivel")
    private boolean disponivel;

    @OneToOne
    @JoinColumn(name = "ultima_localizacao_id")
    private Localizacao ultimaLocalizacao;

    public Colar() {}

    public Colar(String id, int bateria, String nivelSinal, int frequenciaMinutos) {
        this.id = id;
        this.bateria = bateria;
        this.nivelSinal = nivelSinal;
        this.frequenciaMinutos = frequenciaMinutos;
        this.firmware = "v2.4.1";
        this.disponivel = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getBateria() { return bateria; }
    public void setBateria(int bateria) { this.bateria = bateria; }
    public String getNivelSinal() { return nivelSinal; }
    public void setNivelSinal(String nivelSinal) { this.nivelSinal = nivelSinal; }
    public int getFrequenciaMinutos() { return frequenciaMinutos; }
    public void setFrequenciaMinutos(int frequenciaMinutos) { this.frequenciaMinutos = frequenciaMinutos; }
    public String getFirmware() { return firmware; }
    public void setFirmware(String firmware) { this.firmware = firmware; }
    public boolean isDisponivel() { return disponivel; }
    public void setDisponivel(boolean disponivel) { this.disponivel = disponivel; }
    public Localizacao getUltimaLocalizacao() { return ultimaLocalizacao; }
    public void setUltimaLocalizacao(Localizacao ultimaLocalizacao) { this.ultimaLocalizacao = ultimaLocalizacao; }

    @Override
    public String toString() {
        return id + " | Bateria: " + bateria + "% | Sinal: " + nivelSinal;
    }
}