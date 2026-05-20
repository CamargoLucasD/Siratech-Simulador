package backend;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fazendas")
public class Fazenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "nome")
    private String nome;

    @Column(name = "proprietario")
    private String proprietario;

    @Column(name = "municipio")
    private String municipio;

    @Column(name = "estado")
    private String estado;

    @Column(name = "area_total")
    private double areaTotal;

    @Column(name = "area_monitorada")
    private double areaMonitorada;

    @Column(name = "latitude_centro")
    private double latitudeCentro;

    @Column(name = "longitude_centro")
    private double longitudeCentro;

    @Column(name = "raio_metros")
    private double raioMetros;

    @Column(name = "tolerancia_metros")
    private double toleranciaMetros;

    @Column(name = "tipo_area")
    private String tipoArea;

   @OneToMany(fetch = FetchType.EAGER)
   @JoinColumn(name = "fazenda_id")
   private List<Lote> lotes = new ArrayList<>();

    public Fazenda() {}

    public Fazenda(int id, String nome, String proprietario,
                   String municipio, String estado,
                   double latCentro, double lonCentro, double raio) {
        this.id = id;
        this.nome = nome;
        this.proprietario = proprietario;
        this.municipio = municipio;
        this.estado = estado;
        this.latitudeCentro = latCentro;
        this.longitudeCentro = lonCentro;
        this.raioMetros = raio;
        this.toleranciaMetros = 50;
        this.tipoArea = "Circular";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getProprietario() { return proprietario; }
    public void setProprietario(String proprietario) { this.proprietario = proprietario; }
    public String getMunicipio() { return municipio; }
    public void setMunicipio(String municipio) { this.municipio = municipio; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public double getAreaTotal() { return areaTotal; }
    public void setAreaTotal(double areaTotal) { this.areaTotal = areaTotal; }
    public double getAreaMonitorada() { return areaMonitorada; }
    public void setAreaMonitorada(double areaMonitorada) { this.areaMonitorada = areaMonitorada; }
    public double getLatitudeCentro() { return latitudeCentro; }
    public void setLatitudeCentro(double latitudeCentro) { this.latitudeCentro = latitudeCentro; }
    public double getLongitudeCentro() { return longitudeCentro; }
    public void setLongitudeCentro(double longitudeCentro) { this.longitudeCentro = longitudeCentro; }
    public double getRaioMetros() { return raioMetros; }
    public void setRaioMetros(double raioMetros) { this.raioMetros = raioMetros; }
    public double getToleranciaMetros() { return toleranciaMetros; }
    public void setToleranciaMetros(double toleranciaMetros) { this.toleranciaMetros = toleranciaMetros; }
    public String getTipoArea() { return tipoArea; }
    public void setTipoArea(String tipoArea) { this.tipoArea = tipoArea; }
    public List<Lote> getLotes() { return lotes; }
    public void addLote(Lote lote) { this.lotes.add(lote); }

    @Override
    public String toString() { return nome + " - " + municipio + "/" + estado; }
}