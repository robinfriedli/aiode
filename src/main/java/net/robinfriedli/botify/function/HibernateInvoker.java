package net.robinfriedli.botify.function;

import java.util.concurrent.Callable;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.function.modes.HibernateTransactionMode;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.jxp.exec.BaseInvoker;
import net.robinfriedli.jxp.exec.modes.SynchronisationMode;
import org.hibernate.Session;

/**
 * Invoker that runs a task in a hibernate transaction by using the {@link HibernateTransactionMode}
 */
public class HibernateInvoker extends BaseInvoker implements FunctionInvoker<Session> {

    @Nullable
    private final Session session;
    @Nullable
    private final Object synchronisationLock;

    public HibernateInvoker() {
        this(null, null);
    }

    public HibernateInvoker(@Nullable Session session, @Nullable Object synchronisationLock) {
        this.session = session;
        this.synchronisationLock = synchronisationLock;
    }

    public static HibernateInvoker create() {
        return create(null);
    }

    public static HibernateInvoker create(Session session) {
        return create(session, null);
    }

    public static HibernateInvoker create(Object synchronisationLock) {
        return create(null, synchronisationLock);
    }

    public static HibernateInvoker create(Session session, @Nullable Object synchronisationLock) {
        return new HibernateInvoker(session, synchronisationLock);
    }

    public <E> E invoke(Callable<E> callable) {
        Mode mode = Mode.create();
        if (synchronisationLock != null) {
            mode.with(new SynchronisationMode(synchronisationLock));
        }

        try {
            return super.invoke(mode.with(new HibernateTransactionMode(session)), callable);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        }
    }

    public void invoke(Runnable runnable) {
        invoke(() -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public <V> V invoke(Function<Session, V> function) {
        return invoke(() -> function.apply(session != null ? session : StaticSessionProvider.provide()));
    }
}
