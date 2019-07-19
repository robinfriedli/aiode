package net.robinfriedli.botify.boot.tasks;

import java.util.List;

import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Task that ensures that a accessConfiguration exists for the permission command for all guilds
 */
public class CreatePermissionAccessConfigurationTask implements StartupTask {

    private final SessionFactory sessionFactory;

    public CreatePermissionAccessConfigurationTask(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void perform() {
        try (Session session = sessionFactory.openSession()) {
            // find all guild specifications where no access configuration for the permission command exists
            List<GuildSpecification> guildSpecifications = session.createQuery("from " + GuildSpecification.class.getName()
                + " as g where not exists(from " + AccessConfiguration.class.getName() + " as a"
                + " where a.guildSpecification.pk = g.pk and a.commandIdentifier = 'permission')", GuildSpecification.class)
                .getResultList();

            if (!guildSpecifications.isEmpty()) {
                session.beginTransaction();
                for (GuildSpecification guildSpecification : guildSpecifications) {
                    AccessConfiguration accessConfiguration = new AccessConfiguration("permission");
                    session.persist(accessConfiguration);
                    guildSpecification.addAccessConfiguration(accessConfiguration);
                }
                session.getTransaction().commit();
            }
        }
    }
}
