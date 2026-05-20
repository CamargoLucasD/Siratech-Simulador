package backend;

import org.hibernate.Session;
import java.util.*;

public class AlertaService {

    private List<Runnable> listeners = new ArrayList<>();

    public AlertaService() {}

    public Alerta gerarAlerta(Alerta.Tipo tipo, Animal animal, String mensagem) {
        Alerta alerta = new Alerta(0, tipo, animal, mensagem);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.persist(alerta);
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        notificarListeners();
        return alerta;
    }

    public List<Alerta> listarAtivos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Alerta a WHERE a.resolvido = false", Alerta.class).list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Alerta> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Alerta", Alerta.class).list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public boolean resolverAlerta(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            Alerta alerta = session.get(Alerta.class, id);
            if (alerta != null) {
                alerta.setResolvido(true);
                session.merge(alerta);
                session.getTransaction().commit();
                notificarListeners();
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void resolverTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.createMutationQuery(
                    "UPDATE Alerta a SET a.resolvido = true WHERE a.resolvido = false")
                    .executeUpdate();
            session.getTransaction().commit();
            notificarListeners();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int totalAtivos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT COUNT(a) FROM Alerta a WHERE a.resolvido = false", Long.class)
                    .uniqueResult().intValue();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void addListener(Runnable listener) { listeners.add(listener); }
    private void notificarListeners() { listeners.forEach(Runnable::run); }
}
