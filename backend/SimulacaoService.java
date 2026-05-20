package backend;

/**
 * SimulacaoService — ponte entre o simulador e os serviços reais do SIRATECH.
 *
 * Assinaturas reais confirmadas:
 *   RastreamentoService.registrarPosicao(Animal, double, double)
 *   AlertaService.gerarAlerta(Alerta.Tipo, Animal, String)
 *   AlertaService.resolverAlerta(int)
 */
public class SimulacaoService {

    private final RastreamentoService rastreamentoService;
    private final AlertaService       alertaService;
    private final FazendaService      fazendaService;

    public SimulacaoService(RastreamentoService rastreamentoService,
                             AlertaService alertaService,
                             FazendaService fazendaService) {
        this.rastreamentoService = rastreamentoService;
        this.alertaService       = alertaService;
        this.fazendaService      = fazendaService;
    }

    // ── Registro de posição ────────────────────────────────────────────────────

    /**
     * Registra a posição simulada do animal no banco.
     * O RastreamentoService verifica geofence e gera alertas automaticamente.
     */
    public void registrarPosicao(AnimalSimulado a) {
        try {
            Animal animal = a.getAnimal();
            if (animal == null) return;
            rastreamentoService.registrarPosicao(animal, a.getLatitude(), a.getLongitude());
        } catch (Exception e) {
            System.err.println("[SimulacaoService] Erro ao registrar posição: " + e.getMessage());
        }
    }

    // ── Alertas ────────────────────────────────────────────────────────────────

    /**
     * Gera alerta de "fora da área" no banco para o animal.
     * Assinatura real: gerarAlerta(Alerta.Tipo, Animal, String)
     */
    public void gerarAlertaForaDaArea(AnimalSimulado a) {
        try {
            Animal animal = a.getAnimal();
            if (animal == null) return;
            alertaService.gerarAlerta(
                Alerta.Tipo.FORA_DA_AREA,
                animal,
                animal.getNome() + " saiu da área! Brinco: " + animal.getNumeroBrinco()
            );
        } catch (Exception e) {
            System.err.println("[SimulacaoService] Erro ao gerar alerta: " + e.getMessage());
        }
    }

    /**
     * Resolve todos os alertas FORA_DA_AREA ativos do animal.
     * Chamado quando o animal retorna à área (resgate concluído ou volta sozinho).
     */
    public void resolverAlertaRetorno(AnimalSimulado a) {
        try {
            Animal animal = a.getAnimal();
            if (animal == null) return;
            alertaService.listarAtivos().stream()
                .filter(alerta -> alerta.getTipo() == Alerta.Tipo.FORA_DA_AREA)
                .filter(alerta -> alerta.getAnimal() != null
                               && alerta.getAnimal().getId() == animal.getId())
                .forEach(alerta -> alertaService.resolverAlerta(alerta.getId()));
        } catch (Exception e) {
            System.err.println("[SimulacaoService] Erro ao resolver alerta: " + e.getMessage());
        }
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public AlertaService       getAlertaService()       { return alertaService; }
    public RastreamentoService getRastreamentoService() { return rastreamentoService; }
    public FazendaService      getFazendaService()      { return fazendaService; }
}
