package backend;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import io.github.cdimascio.dotenv.Dotenv;

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
                Dotenv dotenv = Dotenv.load();

                String host     = dotenv.get("DB_HOST");
                String port     = dotenv.get("DB_PORT", "5432");
                String dbName   = dotenv.get("DB_NAME");
                String user     = dotenv.get("DB_USER");
                String password = dotenv.get("DB_PASSWORD");

                if (host == null || dbName == null || user == null || password == null) {
                    throw new IllegalStateException(
                        "Variáveis de ambiente obrigatórias não configuradas. " +
                        "Defina DB_HOST, DB_NAME, DB_USER e DB_PASSWORD antes de iniciar a aplicação."
                    );
                }

                config.setProperty("hibernate.connection.url",
                        "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?sslmode=require");
                config.setProperty("hibernate.connection.username", user);
                config.setProperty("hibernate.connection.password", password);

                // Entidades usadas pelo simulador
                config.addAnnotatedClass(Animal.class);
                config.addAnnotatedClass(Colar.class);
                config.addAnnotatedClass(Localizacao.class);
                config.addAnnotatedClass(Lote.class);
                config.addAnnotatedClass(Fazenda.class);
                config.addAnnotatedClass(Alerta.class);
                config.addAnnotatedClass(HistoricoVet.class);

                sessionFactory = config.buildSessionFactory();

            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Erro ao iniciar Hibernate: " + e.getMessage(), e);
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
