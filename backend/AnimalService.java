package backend;

import org.hibernate.Session;
import org.hibernate.query.Query;
import java.util.ArrayList;
import java.util.List;

/**
 * AnimalService do simulador.
 * NÃO tem carregarDadosIniciais() — o simulador não cria dados fictícios.
 * Contém apenas métodos de leitura e escrita usados pela simulação.
 *
 * Esta classe tem prioridade no classpath sobre o AnimalService do SIRATECH.
 */
public class AnimalService {

    public AnimalService() {}

    /** Lista todos os animais com status "Ativo". */
    public List<Animal> listarAtivos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Animal a WHERE a.status = 'Ativo'", Animal.class).list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /** Lista animais ativos de uma fazenda específica. */
    public List<Animal> listarAtivosPorFazenda(int fazendaId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Animal a WHERE a.status = 'Ativo' AND a.fazendaId = :fid",
                    Animal.class)
                    .setParameter("fid", fazendaId)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /** Lista todos os animais de uma fazenda (qualquer status). */
    public List<Animal> listarPorFazenda(int fazendaId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Animal a WHERE a.fazendaId = :fid", Animal.class)
                    .setParameter("fid", fazendaId)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /** Busca animal por ID. Retorna null se não encontrado. */
    public Animal buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Animal.class, id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Busca animal pelo número do brinco. Retorna null se não encontrado. */
    public Animal buscarPorBrinco(String numeroBrinco) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Animal> q = session.createQuery(
                    "FROM Animal a WHERE a.numeroBrinco = :brinco", Animal.class);
            q.setParameter("brinco", numeroBrinco);
            return q.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Persiste alterações num Animal já existente (ex: atualizar status). */
    public void atualizar(Animal animal) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.merge(animal);
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Remove um animal pelo ID. */
    public void remover(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            Animal a = session.get(Animal.class, id);
            if (a != null) session.remove(a);
            session.getTransaction().commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
