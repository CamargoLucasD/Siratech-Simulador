package backend;

import org.hibernate.Session;
import java.util.*;

/**
 * FazendaService do simulador — somente leitura/escrita dos dados reais.
 * NÃO cria dados iniciais para não interferir no banco do SIRATECH.
 */
public class FazendaService {

    public FazendaService() {
        // Sem carregarDadosIniciais — o simulador usa apenas dados reais do SIRATECH
    }

    public Fazenda cadastrar(Fazenda fazenda) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.persist(fazenda);
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fazenda;
    }

    public List<Fazenda> listarTodas() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Fazenda", Fazenda.class).list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Optional<Fazenda> buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return Optional.ofNullable(session.get(Fazenda.class, id));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Fazenda getFazendaPrincipal() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Fazenda", Fazenda.class)
                    .setMaxResults(1)
                    .uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean atualizar(Fazenda fazenda) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.merge(fazenda);
            session.getTransaction().commit();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
