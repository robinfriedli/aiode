package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.jxp.exec.AbstractDelegatingModeWrapper;
import net.robinfriedli.jxp.exec.Invoker;
import net.robinfriedli.jxp.exec.modes.SynchronisationMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import static net.robinfriedli.botify.util.StaticSessionProvider.*;

/**
 * Runs a task in a hibernate transaction, managing commits and rollbacks. This automatically utilises either the session of the
 * current {@link CommandContext} or, if absent, the current thread session using {@link SessionFactory#getCurrentSession()}
 * if no session was provided explicitly. See {@link StaticSessionProvider}.
 */
public class HibernateTransactionMode extends AbstractDelegatingModeWrapper {

    @Nullable
    private Session session;

    public HibernateTransactionMode() {
        this(null);
    }

    public HibernateTransactionMode(@Nullable Session session) {
        this.session = session;
    }

    public static Invoker.Mode getMode() {
        return getMode(null);
    }

    public static Invoker.Mode getMode(Session session) {
        return getMode(session, null);
    }

    public static Invoker.Mode getMode(Object synchronisationLock) {
        return getMode(null, synchronisationLock);
    }

    public static Invoker.Mode getMode(Session session, @Nullable Object synchronisationLock) {
        Invoker.Mode mode = Invoker.Mode.create();
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

}
