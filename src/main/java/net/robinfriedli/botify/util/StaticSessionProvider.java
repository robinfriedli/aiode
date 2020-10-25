package net.robinfriedli.botify.util;

import java.util.function.Consumer;
import java.util.function.Function;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.interceptors.InterceptorChain;
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

    public static void consumeSession(Consumer<Session> consumer) {
        HibernateInvoker.create(provide()).invokeConsumer(consumer);
    }

    public static <E> E invokeWithSession(Function<Session, E> function) {
        return HibernateInvoker.create(provide()).invokeFunction(function);
    }

    public static void consumeSessionWithoutInterceptors(Consumer<Session> sessionConsumer) {
        InterceptorChain.INTERCEPTORS_MUTED.set(true);
        try {
            consumeSession(sessionConsumer);
        } finally {
            InterceptorChain.INTERCEPTORS_MUTED.set(false);
        }
    }

    public static <E> E invokeSessionWithoutInterceptors(Function<Session, E> function) {
        InterceptorChain.INTERCEPTORS_MUTED.set(true);
        try {
            return invokeWithSession(function);
        } finally {
            InterceptorChain.INTERCEPTORS_MUTED.set(false);
        }
    }

}
