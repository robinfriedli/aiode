package net.robinfriedli.botify.function.modes;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import net.robinfriedli.exec.AbstractNestedModeWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Mode that breaks recursion by simply not executing tasks with this mode applied if there already is a task with this
 * mode and the same recursion key running in the current thread.
 */
public class RecursionPreventionMode extends AbstractNestedModeWrapper {

    private static final ThreadLocal<Set<String>> RECURSION_KEYS = ThreadLocal.withInitial(HashSet::new);

    private final String key;

    public RecursionPreventionMode(String key) {
        this.key = key;
    }

    @NotNull
    @Override
    public <T> Callable<T> wrap(@NotNull Callable<T> callable) {
        return () -> {
            Set<String> usedKeys = RECURSION_KEYS.get();
            if (usedKeys.contains(key)) {
                // task was already running in this mode, this is a recursive call -> return
                return null;
            }

            usedKeys.add(key);
            try {
                return callable.call();
            } finally {
                usedKeys.remove(key);
            }
        };
    }
}
