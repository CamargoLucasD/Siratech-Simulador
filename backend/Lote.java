package backend;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lotes")
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "nome")
    private String nome;

    @Column(name = "area_ha")
    private double areaHa;

    @Column(name = "status")
    private String status;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id")
    private List<Animal> animais = new ArrayList<>();

    public Lote() {}

    public Lote(int id, String nome, double areaHa) {
        this.id = id;
        this.nome = nome;
        this.areaHa = areaHa;
        this.status = "Ativo";
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public double getAreaHa() { return areaHa; }
    public void setAreaHa(double areaHa) { this.areaHa = areaHa; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Animal> getAnimais() { return animais; }
    public void addAnimal(Animal a) { animais.add(a); }

    public double getAreaHectares() {
    return areaHa;
    }

public int getCapacidade() {
    return animais != null ? animais.size() : 0;
    }

    @Override
    public String toString() { return nome + " (" + areaHa + " ha)"; }
}