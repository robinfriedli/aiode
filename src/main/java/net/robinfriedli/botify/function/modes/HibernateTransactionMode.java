package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;
import javax.persistence.RollbackException;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.exec.AbstractNestedModeWrapper;
import net.robinfriedli.exec.Mode;
import net.robinfriedli.exec.modes.SynchronisationMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import static net.robinfriedli.botify.persist.StaticSessionProvider.*;

/**
 * Runs a task in a hibernate transaction, managing commits and rollbacks. This automatically utilises either the session of the
 * current {@link CommandContext} or, if absent, the current thread session using {@link SessionFactory#getCurrentSession()}
 * if no session was provided explicitly. See {@link StaticSessionProvider}.
 */
public class HibernateTransactionMode extends AbstractNestedModeWrapper {

    @Nullable
    private final Session session;

    public HibernateTransactionMode() {
        this(null);
    }

    public HibernateTransactionMode(@Nullable Session session) {
        this.session = session;
    }

    public static Mode getMode() {
        return getMode(null);
    }

    public static Mode getMode(Session session) {
        return getMode(session, null);
    }

    public static Mode getMode(Object synchronisationLock) {
        return getMode(null, synchronisationLock);
    }

    public static Mode getMode(Session session, @Nullable Object synchronisationLock) {
        Mode mode = Mode.create();
        if (synchronisationLock != null) {
            mode.with(new SynchronisationMode(synchronisationLock));
        }

        return mode.with(new HibernateTransactionMode(session));
    }

    @Override
    public <E> Callable<E> wrap(Callable<E> callable) {
        return new TransactionCallable<>(session, callable);
    }

    private static class TransactionCallable<E> implements Callable<E> {

        @Nullable
        private final Session session;
        private final Callable<E> callableToWrap;

        private TransactionCallable(@Nullable Session session, Callable<E> callableToWrap) {
            this.session = session;
            this.callableToWrap = callableToWrap;
        }

        @Override
        public E call() throws Exception {
            Session session = this.session;
            if (session == null) {
                session = provide();
            }

            boolean commitRequired = false;
            boolean committed = false;
            if (!session.getTransaction().isActive()) {
                session.beginTransaction();
                commitRequired = true;
            }

            try {
                E result = callableToWrap.call();

                if (commitRequired) {
                    try {
                        session.getTransaction().commit();
                        committed = true;
                    } catch (RollbackException e) {
                        // now that hibernate is bootstrapped via JPA by spring boot as of botify 2.0, hibernate now wraps
                        // exceptions thrown during commit; with native bootstrapping hibernate simply threw the exception
                        if (e.getCause() instanceof Exception) {
                            throw (Exception) e.getCause();
                        } else {
                            throw e;
                        }
                    }
                }

                return result;
            } finally {
                if (commitRequired && !committed) {
                    session.getTransaction().markRollbackOnly();
                    session.getTransaction().rollback();
                }
            }
        }
    }

}
