package backend;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "historico_vet")
public class HistoricoVet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "animal_id")
    private Animal animal;

    @ManyToOne
    @JoinColumn(name = "fazenda_id")
    private Fazenda fazenda;

    @Column(name = "data_atendimento")
    private LocalDate dataAtendimento;

    @Column(name = "procedimento")
    private String procedimento;

    @Column(name = "veterinario")
    private String veterinario;

    @Column(name = "observacoes", length = 1000)
    private String observacoes;

    public HistoricoVet() {}

    public HistoricoVet(Animal animal, Fazenda fazenda, LocalDate data,
                        String procedimento, String veterinario, String observacoes) {
        this.animal = animal;
        this.fazenda = fazenda;
        this.dataAtendimento = data;
        this.procedimento = procedimento;
        this.veterinario = veterinario;
        this.observacoes = observacoes;
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public int getId() { return id; }
    public Animal getAnimal() { return animal; }
    public void setAnimal(Animal animal) { this.animal = animal; }
    public Fazenda getFazenda() { return fazenda; }
    public void setFazenda(Fazenda fazenda) { this.fazenda = fazenda; }
    public LocalDate getDataAtendimento() { return dataAtendimento; }
    public void setDataAtendimento(LocalDate dataAtendimento) { this.dataAtendimento = dataAtendimento; }
    public String getProcedimento() { return procedimento; }
    public void setProcedimento(String procedimento) { this.procedimento = procedimento; }
    public String getVeterinario() { return veterinario; }
    public void setVeterinario(String veterinario) { this.veterinario = veterinario; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public String getDataStr() { return dataAtendimento != null ? dataAtendimento.format(FMT) : "—"; }
}
