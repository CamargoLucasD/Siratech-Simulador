package backend;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * HibernateUtil do SIMULADOR.
 * Diferença em relação ao ERP: não registra Vacina, Transacao nem Usuario,
 * pois essas entidades não existem no projeto do simulador.
 */
public class HibernateUtil {

    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                Configuration config = new Configuration();
                config.configure("hibernate.cfg.xml");

                // Entidades usadas pelo simulador
                config.addAnnotatedClass(Animal.class);
                config.addAnnotatedClass(Colar.class);
                config.addAnnotatedClass(Localizacao.class);
                config.addAnnotatedClass(Lote.class);
                config.addAnnotatedClass(Fazenda.class);
                config.addAnnotatedClass(Alerta.class);
                config.addAnnotatedClass(HistoricoVet.class);

                sessionFactory = config.buildSessionFactory();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Erro ao iniciar Hibernate: " + e.getMessage());
            }
        }
        return sessionFactory;
    }

    public static void fechar() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
