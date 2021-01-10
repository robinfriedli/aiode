package net.robinfriedli.botify.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Allows static access to a thread local storage of objects useful during the execution of a thread. The best example
 * is the{@link ExecutionContext} but this is also used to store message channel or command objects used in uncaught
 * exception handlers created by thread factories or other scenarios where the value may not be passed directly.
 */
public class ThreadContext {

    private final Map<String, Object> installedContexts = new HashMap<>();
    @Nullable
    private Map<String, Object> inheritedContexts;

    public ThreadContext() {
        this(null);
    }

    public ThreadContext(@Nullable Map<String, Object> inheritedContexts) {
        this.inheritedContexts = inheritedContexts;
    }

    @Nullable
    public <T> T get(Class<T> contextType) {
        return optional(contextType).orElse(null);
    }

    @Nullable
    public <T> T get(String key, Class<T> contextType) {
        return optional(key, contextType).orElse(null);
    }

    public <T> Optional<T> optional(Class<T> contextType) {
        return Optional
            .ofNullable(installedContexts.get(contextType.getName()))
            .or(() -> installedContexts.values().stream().filter(contextType::isInstance).findAny())
            .map(contextType::cast);
    }

    public <T> Optional<T> optional(String key, Class<T> contextType) {
        return Optional.ofNullable(installedContexts.get(key)).map(contextType::cast);
    }

    public <T> T require(Class<T> contextType) {
        return optional(contextType).orElseThrow();
    }

    public <T> T require(String key, Class<T> contextType) {
        return optional(key, contextType).orElseThrow();
    }

    public <T> T getOrCompute(Class<T> contextType, Supplier<T> supplier) {
        return optional(contextType).orElseGet(() -> {
            T t = supplier.get();
            install(t);
            return t;
        });
    }

    public <T> T getOrCompute(String key, Class<T> contextType, Supplier<T> supplier) {
        return optional(key, contextType).orElseGet(() -> {
            T t = supplier.get();
            install(t);
            return t;
        });
    }

    public void install(Object o) {
        install(o.getClass().getName(), o);
    }

    @SuppressWarnings("rawtypes")
    public void install(String key, Object o) {
        if (o instanceof ForkableThreadContext) {
            ((ForkableThreadContext) o).setThread(Thread.currentThread());
        }

        installedContexts.put(key, o);
    }

    @SuppressWarnings("unchecked")
    public <T> T drop(Class<T> contextType) {
        return (T) installedContexts.remove(contextType.getName());
    }

    public Object drop(String key) {
        return installedContexts.remove(key);
    }

    public void clear() {
        for (Object context : installedContexts.values()) {
            if (context instanceof CloseableThreadContext) {
                ((CloseableThreadContext) context).close();
            }
        }
        installedContexts.clear();
    }

    public boolean isInstalled(Class<?> contextType) {
        return installedContexts.containsKey(contextType.getName());
    }

    public boolean isInstalled(String key) {
        return installedContexts.containsKey(key);
    }

    /**
     * @return a new ThreadContext based on this one with the same content, invoking {@link ForkableThreadContext#fork()}
     * for each installed Context that implements that interface. The new ThreadContext will receive a copy of the map
     * of installed contexts of the this ThreadContext as inheritedContexts. Once the new ThreadContext is installed via
     * {@link ThreadContext.Current#installExplicitly(ThreadContext)} the inherited contexts are installed on the new
     * ThreadContext and {@link ForkableThreadContext#fork()} is called for each applicable context.
     * To be used in forked tasks.
     */
    public ThreadContext fork() {
        Map<String, Object> inheritedContexts = installedContexts
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new ThreadContext(inheritedContexts);
    }

    public static class Current {

        private static final ThreadLocal<ThreadContext> THREAD_CONTEXT = ThreadLocal.withInitial(ThreadContext::new);

        public static void installExplicitly(ThreadContext threadContext) {
            THREAD_CONTEXT.set(threadContext);
            if (threadContext.inheritedContexts != null) {
                for (Map.Entry<String, Object> contextMapping : threadContext.inheritedContexts.entrySet()) {
                    String key = contextMapping.getKey();
                    Object context = contextMapping.getValue();
                    if (context instanceof ForkableThreadContext) {
                        threadContext.install(key, ((ForkableThreadContext<?>) context).fork());
                    } else {
                        threadContext.install(key, context);
                    }
                }
                threadContext.inheritedContexts = null;
            }
        }

        public static ThreadContext get() {
            return THREAD_CONTEXT.get();
        }

        @Nullable
        public static <T> T get(Class<T> contextType) {
            return get().get(contextType);
        }

        @Nullable
        public static <T> T get(String key, Class<T> contextType) {
            return get().get(key, contextType);
        }

        public static <T> Optional<T> optional(Class<T> contextType) {
            return get().optional(contextType);
        }

        public static <T> Optional<T> optional(String key, Class<T> contextType) {
            return get().optional(key, contextType);
        }

        public static <T> T require(Class<T> contextType) {
            return get().require(contextType);
        }

        public static <T> T require(String key, Class<T> contextType) {
            return get().require(key, contextType);
        }

        public static void install(Object o) {
            get().install(o);
        }

        public static void install(String key, Object o) {
            get().install(key, o);
        }

        public static <T> T drop(Class<T> contextType) {
            return get().drop(contextType);
        }

        public static void drop(String key) {
            get().drop(key);
        }

        public static void clear() {
            get().clear();
        }

        public static boolean isInstalled(Class<?> contextType) {
            return get().isInstalled(contextType);
        }

        public static boolean isInstalled(String key) {
            return get().isInstalled(key);
        }

    }

}
