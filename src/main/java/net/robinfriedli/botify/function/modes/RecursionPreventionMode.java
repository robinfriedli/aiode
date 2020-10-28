package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import net.robinfriedli.exec.AbstractNestedModeWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Mode that breaks recursion by simply not executing tasks with this mode applied if there already is a task with this
 * mode running in the current thread.
 */
public class RecursionPreventionMode extends AbstractNestedModeWrapper {

    private static final ThreadLocal<Boolean> IS_RUNNING = ThreadLocal.withInitial(() -> false);

    @NotNull
    @Override
    public <T> Callable<T> wrap(@NotNull Callable<T> callable) {
        return () -> {
            if (IS_RUNNING.get()) {
                // task was already running in this mode, this is a recursive call -> return
                return null;
            }

            IS_RUNNING.set(true);
            try {
                return callable.call();
            } finally {
                IS_RUNNING.set(false);
            }
        };
    }
}
