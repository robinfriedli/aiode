package net.robinfriedli.aiode.function.modes;

import java.util.Set;
import java.util.concurrent.Callable;

import com.google.common.collect.Sets;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.exec.AbstractNestedModeWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Mode that breaks recursion by simply not executing tasks with this mode applied if there already is a task with this
 * mode and the same recursion key running in the current thread.
 */
public class RecursionPreventionMode extends AbstractNestedModeWrapper {

    private final String key;

    public RecursionPreventionMode(String key) {
        this.key = key;
    }

    @NotNull
    @Override
    public <T> Callable<T> wrap(@NotNull Callable<T> callable) {
        return () -> {
            ThreadContext threadContext = ThreadContext.Current.get();
            Set<String> usedKeys;
            if (threadContext.isInstalled("recursion_prevention_keys")) {
                //noinspection unchecked
                usedKeys = threadContext.require("recursion_prevention_keys", Set.class);

                if (!usedKeys.add(key)) {
                    // task was already running in this mode, this is a recursive call -> return
                    return null;
                }
            } else {
                usedKeys = Sets.newHashSet(key);
                threadContext.install("recursion_prevention_keys", usedKeys);
            }

            try {
                return callable.call();
            } finally {
                usedKeys.remove(key);
            }
        };
    }
}
