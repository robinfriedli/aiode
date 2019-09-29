package net.robinfriedli.botify.util;

import java.util.function.Consumer;
import java.util.function.Function;

import net.robinfriedli.botify.command.CommandContext;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Utility class to statically provide a hibernate session or run a code snippet with a hibernate session based on the
 * {@link CommandContext} of the current thread
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
        invokeWithSession(session -> {
            consumer.accept(session);
            return null;
        });
    }

    public static <E> E invokeWithSession(Function<Session, E> function) {
        Session session = provide();
        boolean commitRequired = false;
        if (!CommandContext.Current.isSet()) {
            if (session.getTransaction() == null || !session.getTransaction().isActive()) {
                session.beginTransaction();
                commitRequired = true;
            }
        }
        try {
            return function.apply(session);
        } catch (Throwable e) {
            if (commitRequired) {
                session.getTransaction().rollback();
                // make sure this transaction is not committed in the finally block, which would throw an exception that
                // overrides the current exception
                commitRequired = false;
            }
            throw e;
        } finally {
            if (commitRequired) {
                session.getTransaction().commit();
            }
        }
    }

}
