package backend;

import org.hibernate.Session;
import org.hibernate.query.Query;
import java.time.LocalDateTime;
import java.util.*;

public class RastreamentoService {

    private GeofenceService geofenceService;
    private FazendaService fazendaService;
    private AlertaService alertaService;

    public RastreamentoService(GeofenceService geo, FazendaService faz, AlertaService alert) {
        this.geofenceService = geo;
        this.fazendaService  = faz;
        this.alertaService   = alert;
    }

    public Localizacao registrarPosicao(Animal animal, double latitude, double longitude) {
        Localizacao loc = new Localizacao(0, latitude, longitude, animal);
        loc.setTimestamp(LocalDateTime.now());

        Fazenda fazenda = fazendaService.getFazendaPrincipal();
        if (fazenda != null) {
            String statusGeo = geofenceService.verificarStatus(loc, fazenda);
            loc.setStatus(statusGeo);
            if ("Fora".equals(statusGeo)) {
                alertaService.gerarAlerta(
                        Alerta.Tipo.FORA_DA_AREA, animal,
                        animal.getNome() + " saiu da área! Brinco: " + animal.getNumeroBrinco());
            }
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.persist(loc);

            if (animal.getColar() != null) {
                animal.getColar().setUltimaLocalizacao(loc);
                session.merge(animal.getColar());
            }

            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return loc;
    }

    public List<Localizacao> buscarHistoricoPorAnimal(Animal animal) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Localizacao> query = session.createQuery(
                    "FROM Localizacao l WHERE l.animal.id = :animalId ORDER BY l.timestamp DESC",
                    Localizacao.class);
            query.setParameter("animalId", animal.getId());
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Localizacao> buscarUltimasPosicoes(int quantidade) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Localizacao l ORDER BY l.timestamp DESC", Localizacao.class)
                    .setMaxResults(quantidade)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public int totalRegistros() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("SELECT COUNT(l) FROM Localizacao l", Long.class)
                    .uniqueResult().intValue();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
