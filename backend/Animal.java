package backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "animais")
public class Animal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "nome")
    private String nome;

    @Column(name = "raca")
    private String raca;

    @Column(name = "numero_brinco", unique = true)
    private String numeroBrinco;

    @Column(name = "sexo")
    private String sexo;

    @Column(name = "peso")
    private double peso;

    @Column(name = "data_nascimento")
    private LocalDateTime dataNascimento;

    @Column(name = "lote")
    private String lote;

    @Column(name = "fazenda_id")
    private Integer fazendaId;

    @Column(name = "fazenda_nome")
    private String fazendaNome;

    @Column(name = "status")
    private String status;

    @Column(name = "observacoes")
    private String observacoes;

    @OneToOne
    @JoinColumn(name = "colar_id")
    private Colar colar;

    public Animal() {}

    public Animal(int id, String nome, String raca, String numeroBrinco,
                  String sexo, double peso, String lote) {
        this.id = id;
        this.nome = nome;
        this.raca = raca;
        this.numeroBrinco = numeroBrinco;
        this.sexo = sexo;
        this.peso = peso;
        this.lote = lote;
        this.status = "Ativo";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getRaca() { return raca; }
    public void setRaca(String raca) { this.raca = raca; }
    public String getNumeroBrinco() { return numeroBrinco; }
    public void setNumeroBrinco(String numeroBrinco) { this.numeroBrinco = numeroBrinco; }
    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }
    public double getPeso() { return peso; }
    public void setPeso(double peso) { this.peso = peso; }
    public LocalDateTime getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDateTime dataNascimento) { this.dataNascimento = dataNascimento; }
    public String getLote() { return lote; }
    public void setLote(String lote) { this.lote = lote; }
    public Integer getFazendaId() { return fazendaId; }
    public void setFazendaId(Integer fazendaId) { this.fazendaId = fazendaId; }
    public String getFazendaNome() { return fazendaNome; }
    public void setFazendaNome(String fazendaNome) { this.fazendaNome = fazendaNome; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public Colar getColar() { return colar; }
    public void setColar(Colar colar) { this.colar = colar; }

    @Override
    public String toString() { return nome + " [" + numeroBrinco + "] - " + raca; }
}