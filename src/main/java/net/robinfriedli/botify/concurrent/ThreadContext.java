package net.robinfriedli.botify.concurrent;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

public class ThreadContext {

    private final List<Object> installedContexts = Lists.newArrayList();

    @Nullable
    public <T> T get(Class<T> contextType) {
        return optional(contextType).orElse(null);
    }

    public <T> Optional<T> optional(Class<T> contextType) {
        return installedContexts.stream().filter(contextType::isInstance).map(contextType::cast).findAny();
    }

    public <T> T require(Class<T> contextType) {
        return optional(contextType).orElseThrow();
    }

    public void install(Object o) {
        drop(o.getClass());
        installedContexts.add(o);
    }

    public boolean drop(Class<?> contextType) {
        return installedContexts.removeIf(contextType::isInstance);
    }

    public void clear() {
        installedContexts.clear();
    }

    public boolean isInstalled(Class<?> contextType) {
        return installedContexts.stream().anyMatch(contextType::isInstance);
    }

    public static class Current {

        private static final ThreadLocal<ThreadContext> THREAD_CONTEXT = ThreadLocal.withInitial(ThreadContext::new);

        public static ThreadContext get() {
            return THREAD_CONTEXT.get();
        }

        @Nullable
        public static <T> T get(Class<T> contextType) {
            return get().get(contextType);
        }

        public static <T> Optional<T> optional(Class<T> contextType) {
            return get().optional(contextType);
        }

        public static <T> T require(Class<T> contextType) {
            return get().require(contextType);
        }

        public static void install(Object o) {
            get().install(o);
        }

        public static boolean drop(Class<?> contextType) {
            return get().drop(contextType);
        }

        public static void clear() {
            get().clear();
        }

        public static boolean isInstalled(Class<?> contextType) {
            return get().isInstalled(contextType);
        }

    }

}
