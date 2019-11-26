package net.robinfriedli.botify.util;

import java.util.function.Consumer;
import java.util.function.Function;

import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.function.AutoTransactionInvoker;
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

        if (CommandContext.Current.isSet()) {
            return CommandContext.Current.require().getSession();
        }

        return sessionFactory.getCurrentSession();
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            throw new IllegalStateException("SessionFactory has not been set up yet");
        }

        return sessionFactory;
    }

    public static void invokeWithSession(Consumer<Session> consumer) {
        AutoTransactionInvoker.create(provide()).invoke(consumer);
    }

    public static <E> E invokeWithSession(Function<Session, E> function) {
        return AutoTransactionInvoker.create(provide()).invoke(function);
    }

}
