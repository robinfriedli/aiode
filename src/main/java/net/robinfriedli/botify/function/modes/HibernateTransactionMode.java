package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.exec.AbstractNestedModeWrapper;
import net.robinfriedli.exec.Mode;
import net.robinfriedli.exec.modes.SynchronisationMode;
import org.hibernate.SessionFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Runs a task in a hibernate transaction, managing commits and rollbacks. This utilises either the session of the
 * current {@link CommandContext} or, if absent, the current thread session using {@link SessionFactory#getCurrentSession()}.
 * See {@link StaticSessionProvider}.
 */
public class HibernateTransactionMode extends AbstractNestedModeWrapper {

    public static Mode getMode() {
        return getMode(null);
    }

    public static Mode getMode(@Nullable Object synchronisationLock) {
        Mode mode = Mode.create();
        if (synchronisationLock != null) {
            mode.with(new SynchronisationMode(synchronisationLock));
        }

        return mode.with(new HibernateTransactionMode());
    }

    @Override
    public <E> @NotNull Callable<E> wrap(@NotNull Callable<E> callable) {
        return () -> StaticSessionProvider.invokeWithSession(session -> {
            boolean isNested = false;
            if (session.getTransaction() == null || !session.getTransaction().isActive()) {
                session.beginTransaction();
            } else {
                isNested = true;
            }
            if (isNested) {
                try {
                    return callable.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CommandRuntimeException(e);
                }
            }
            E retVal;
            try {
                retVal = callable.call();
                session.getTransaction().commit();
            } catch (UserException e) {
                session.getTransaction().rollback();
                throw e;
            } catch (Exception e) {
                session.getTransaction().rollback();
                throw new RuntimeException("Exception in invoked callable. Transaction rolled back.", e);
            }
            return retVal;
        });
    }

}
