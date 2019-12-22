package net.robinfriedli.botify.persist;

import java.util.function.Consumer;
import java.util.function.Function;

import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.function.HibernateInvoker;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Utility class to statically provide a hibernate session or run a code snippet with a hibernate session based on the
 * {@link CommandContext} of the current thread. This offers same functionality as the {@link HibernateComponent} but
 * from a static context or from outside a spring component.
 */
public class StaticSessionProvider {

    public static SessionFactory sessionFactory;

    public static Session provide() {
        SessionFactory sessionFactory = getSessionFactory();
        return CommandContext.Current.optional().map(CommandContext::getSession).orElse(sessionFactory.getCurrentSession());
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            throw new IllegalStateException("SessionFactory has not been set up yet");
        }

        return sessionFactory;
    }

    public static void invokeWithSession(Consumer<Session> consumer) {
        HibernateInvoker.create(provide()).invoke(consumer);
    }

    public static <E> E invokeWithSession(Function<Session, E> function) {
        return HibernateInvoker.create(provide()).invoke(function);
    }

}
