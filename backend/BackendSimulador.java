package backend;

import java.util.List;

/**
 * Singleton central do simulador.
 * Mesmo padrão do Backend.java do SIRATECH.
 *
 * Expõe getters para todos os serviços e para a fazenda principal,
 * conforme exigido pelo frontend (SimuladorFrame, PainelLateral).
 */
public class BackendSimulador {

    public final AnimalService       animalService;
    public final FazendaService      fazendaService;
    public final GeofenceService     geofenceService;
    public final AlertaService       alertaService;
    public final RastreamentoService rastreamentoService;
    public final SimulacaoService    simulacaoService;
    public final SimulacaoEngine     engine;

    private final Fazenda fazenda;

    private static BackendSimulador instancia;

    private BackendSimulador() {
        this.animalService       = new AnimalService();
        this.fazendaService      = new FazendaService();
        this.geofenceService     = new GeofenceService();
        this.alertaService       = new AlertaService();
        this.rastreamentoService = new RastreamentoService(
                geofenceService, fazendaService, alertaService);
        this.simulacaoService    = new SimulacaoService(
                rastreamentoService, alertaService, fazendaService);

        Fazenda f = fazendaService.getFazendaPrincipal();
        if (f == null) {
            throw new RuntimeException(
                "Nenhuma fazenda cadastrada no SIRATECH.\n" +
                "Cadastre uma fazenda antes de iniciar o simulador.");
        }
        this.fazenda = f;

        this.engine = new SimulacaoEngine(simulacaoService, fazenda);

        // Carrega apenas animais ativos vinculados à fazenda principal.
        // Animais sem fazenda_id (ex: dados de teste) são ignorados.
        final int fazendaId = fazenda.getId();
        List<Animal> animaisDB = animalService.listarAtivos().stream()
            .filter(a -> a.getFazendaId() != null && a.getFazendaId() == fazendaId)
            .toList();

        if (animaisDB.isEmpty()) {
            System.out.println("[BackendSimulador] Nenhum animal vinculado à fazenda id="
                + fazendaId + ". Verifique se os animais foram cadastrados com "
                + "a fazenda correta no SIRATECH.");
        }

        engine.carregarAnimais(animaisDB);

        // Encerra o Hibernate ao fechar o simulador
        Runtime.getRuntime().addShutdownHook(new Thread(HibernateUtil::fechar));
    }

    public static BackendSimulador getInstance() {
        if (instancia == null) instancia = new BackendSimulador();
        return instancia;
    }

    // ── Getters exigidos pelo frontend ────────────────────────────────────────

    /** Fazenda principal usada na simulação (nunca null após construção). */
    public Fazenda getFazenda() {
        return fazenda;
    }

    /** Engine de simulação (timer, animais, resgatadores). */
    public SimulacaoEngine getEngine() {
        return engine;
    }

    /** Serviço de alertas do SIRATECH (para listar alertas ativos no painel). */
    public AlertaService getAlertaService() {
        return alertaService;
    }

    /** Serviço de animais do simulador. */
    public AnimalService getAnimalService() {
        return animalService;
    }

    /** Serviço de fazenda do simulador. */
    public FazendaService getFazendaService() {
        return fazendaService;
    }

    /** Serviço de rastreamento do SIRATECH. */
    public RastreamentoService getRastreamentoService() {
        return rastreamentoService;
    }

    /** Serviço de geofence do SIRATECH. */
    public GeofenceService getGeofenceService() {
        return geofenceService;
    }

    /** Serviço de simulação (ponte com o SIRATECH). */
    public SimulacaoService getSimulacaoService() {
        return simulacaoService;
    }
}
