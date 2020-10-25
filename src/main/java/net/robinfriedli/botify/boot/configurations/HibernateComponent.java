package net.robinfriedli.botify.boot.configurations;

import java.util.function.Consumer;
import java.util.function.Function;

import javax.persistence.EntityManagerFactory;

import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.botify.persist.interceptors.InterceptorChain;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan("net.robinfriedli.botify.entities")
public class HibernateComponent {

    private final EntityManagerFactory entityManagerFactory;

    public HibernateComponent(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        StaticSessionProvider.sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
    }

    public Session getCurrentSession() {
        SessionFactory sessionFactory = getSessionFactory();
        return ExecutionContext.Current.optional().map(ExecutionContext::getSession).orElse(sessionFactory.getCurrentSession());
    }

    public SessionFactory getSessionFactory() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        if (sessionFactory == null) {
            throw new IllegalStateException("SessionFactory has not been set up yet");
        }

        return sessionFactory;
    }

    public void consumeSession(Consumer<Session> consumer) {
        HibernateInvoker.create(getCurrentSession()).invokeConsumer(consumer);
    }

    public <E> E invokeWithSession(Function<Session, E> function) {
        return HibernateInvoker.create(getCurrentSession()).invokeFunction(function);
    }

    public void consumeSessionWithoutInterceptors(Consumer<Session> sessionConsumer) {
        InterceptorChain.INTERCEPTORS_MUTED.set(true);
        try {
            consumeSession(sessionConsumer);
        } finally {
            InterceptorChain.INTERCEPTORS_MUTED.set(false);
        }
    }

    public <E> E invokeSessionWithoutInterceptors(Function<Session, E> function) {
        InterceptorChain.INTERCEPTORS_MUTED.set(true);
        try {
            return invokeWithSession(function);
        } finally {
            InterceptorChain.INTERCEPTORS_MUTED.set(false);
        }
    }

}
