package net.robinfriedli.botify.function;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.function.modes.HibernateTransactionMode;
import net.robinfriedli.exec.BaseInvoker;
import net.robinfriedli.exec.Mode;
import net.robinfriedli.exec.modes.SynchronisationMode;
import org.jetbrains.annotations.NotNull;

/**
 * Invoker that runs a task in a hibernate transaction by using the {@link HibernateTransactionMode}
 */
public class HibernateInvoker extends BaseInvoker {

    private final Object synchronisationLock;

    public HibernateInvoker(Object synchronisationLock) {
        this.synchronisationLock = synchronisationLock;
    }

    public static HibernateInvoker create() {
        return create(null);
    }

    public static HibernateInvoker create(@Nullable Object synchronisationLock) {
        return new HibernateInvoker(synchronisationLock);
    }

    @Override
    public <E> E invoke(@NotNull Mode mode, @NotNull Callable<E> task) {
        if (synchronisationLock != null) {
            mode.with(new SynchronisationMode(synchronisationLock));
        }

        try {
            return super.invoke(mode.with(new HibernateTransactionMode()), task);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        }
    }

    public <E> E invoke(Callable<E> callable) {
        return invoke(Mode.create(), callable);
    }

    public void invoke(Runnable runnable) {
        invoke(() -> {
            runnable.run();
            return null;
        });
    }

}
