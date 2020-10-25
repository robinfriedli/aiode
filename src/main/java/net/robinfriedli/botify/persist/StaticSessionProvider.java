package net.robinfriedli.botify.persist;

import java.util.function.Consumer;
import java.util.function.Function;

import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.persist.interceptors.InterceptorChain;
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
        return ExecutionContext.Current.optional().map(ExecutionContext::getSession).orElse(sessionFactory.getCurrentSession());
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
