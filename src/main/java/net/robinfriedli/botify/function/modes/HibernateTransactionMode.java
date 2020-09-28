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
    private Session session;

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
        if (session == null) {
            session = provide();
        }
        return new TransactionCallable<>(session, callable);
    }

    private static class TransactionCallable<E> implements Callable<E> {

        private final Session session;
        private final Callable<E> callableToWrap;

        private TransactionCallable(Session session, Callable<E> callableToWrap) {
            this.session = session;
            this.callableToWrap = callableToWrap;
        }

        @Override
        public E call() throws Exception {
            boolean commitRequired = false;
            if (!session.getTransaction().isActive()) {
                session.beginTransaction();
                commitRequired = true;
            }
            try {
                return callableToWrap.call();
            } catch (Throwable e) {
                if (commitRequired) {
                    session.getTransaction().markRollbackOnly();
                    session.getTransaction().rollback();
                    // make sure this transaction is not committed in the finally block, which would throw an exception that
                    // overrides the current exception
                    commitRequired = false;
                }

                throw e;
            } finally {
                if (commitRequired) {
                    try {
                        session.getTransaction().commit();
                    } catch (RollbackException e) {
                        // now that hibernate is bootstrapped via JPA by spring boot as of botify 2.0, hibernate now wraps
                        // exceptions thrown during commit; with native bootstrapping hibernate simply threw the exception
                        if (e.getCause() instanceof Exception) {
                            // commitRequired is never true when an exception occurred, so it is safe to throw an
                            // exception here without potentially swallowing another one
                            //noinspection ThrowFromFinallyBlock
                            throw (Exception) e.getCause();
                        }
                    }
                }
            }
        }
    }

}
