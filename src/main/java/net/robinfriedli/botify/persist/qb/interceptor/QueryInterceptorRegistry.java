package net.robinfriedli.botify.persist.qb.interceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import net.robinfriedli.botify.util.MutableTuple2;

/**
 * Query interceptor registry that makes sure it cannot contain several interceptor instances of the same type by mapping
 * them to their class and enables enabling / disabling interceptors
 */
public class QueryInterceptorRegistry {

    private final Map<Class<? extends QueryInterceptor>, MutableTuple2<QueryInterceptor, Boolean>> interceptors;

    public QueryInterceptorRegistry(QueryInterceptor... interceptors) {
        this.interceptors = new HashMap<>();
        addInterceptors(interceptors);
    }

    private QueryInterceptorRegistry(Map<Class<? extends QueryInterceptor>, MutableTuple2<QueryInterceptor, Boolean>> interceptors) {
        this.interceptors = interceptors;
    }

    public Stream<QueryInterceptor> getInterceptors(boolean enabledOnly) {
        return interceptors.values().stream()
            .filter(interceptorTuple -> !enabledOnly || interceptorTuple.getRight())
            .map(MutableTuple2::getLeft);
    }

    public void addInterceptors(QueryInterceptor... interceptors) {
        for (QueryInterceptor interceptor : interceptors) {
            this.interceptors.put(interceptor.getClass(), MutableTuple2.of(interceptor, true));
        }
    }

    public void disableInterceptors(Class<?>... interceptorClasses) {
        if (interceptorClasses.length == 0) {
            interceptors.values().forEach(tuple -> tuple.setRight(false));
        } else {
            for (Class<?> interceptorClass : interceptorClasses) {
                MutableTuple2<QueryInterceptor, Boolean> interceptorTuple = interceptors.get(interceptorClass);
                if (interceptorTuple != null) {
                    interceptorTuple.setRight(false);
                }
            }
        }
    }

    public QueryInterceptorRegistry copy() {
        Map<Class<? extends QueryInterceptor>, MutableTuple2<QueryInterceptor, Boolean>> newMap = new HashMap<>();
        for (Map.Entry<Class<? extends QueryInterceptor>, MutableTuple2<QueryInterceptor, Boolean>> entry : interceptors.entrySet()) {
            Class<? extends QueryInterceptor> type = entry.getKey();
            MutableTuple2<QueryInterceptor, Boolean> existingTuple = entry.getValue();
            MutableTuple2<QueryInterceptor, Boolean> newTuple = MutableTuple2.of(existingTuple.getLeft(), existingTuple.getRight());
            newMap.put(type, newTuple);
        }

        return new QueryInterceptorRegistry(newMap);
    }

}
