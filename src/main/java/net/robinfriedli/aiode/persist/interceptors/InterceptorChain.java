package net.robinfriedli.aiode.persist.interceptors;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Iterator;

import net.robinfriedli.aiode.util.InjectorService;
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

/**
 * Used to create a chain of hibernate interceptors
 */
public class InterceptorChain extends EmptyInterceptor {

    public static final ThreadLocal<Boolean> INTERCEPTORS_MUTED = ThreadLocal.withInitial(() -> false);

    private static final Interceptor EMPTY_INTERCEPTOR = new Interceptor() {
    };

    private final Interceptor first;

    public InterceptorChain(Interceptor first) {
        this.first = first;
    }

    @SafeVarargs
    public static InterceptorChain of(Class<? extends ChainableInterceptor>... interceptors) {
        if (interceptors.length == 0) {
            return new InterceptorChain(EMPTY_INTERCEPTOR);
        }

        Iterator<Class<? extends ChainableInterceptor>> chain = Arrays.stream(interceptors).iterator();
        return new InterceptorChain(instantiate(chain.next(), chain));
    }

    @SuppressWarnings("unchecked")
    private static ChainableInterceptor instantiate(Class<? extends ChainableInterceptor> interceptorClass,
                                                    Iterator<Class<? extends ChainableInterceptor>> next) {
        Constructor<?>[] constructors = interceptorClass.getConstructors();
        if (constructors.length == 0) {
            throw new IllegalStateException(interceptorClass.getSimpleName() + " does not have any public constructors");
        }

        Constructor<ChainableInterceptor> constructor = (Constructor<ChainableInterceptor>) constructors[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        int parameterCount = constructor.getParameterCount();
        Object[] parameters = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.equals(Interceptor.class)) {
                if (next.hasNext()) {
                    parameters[i] = instantiate(next.next(), next);
                } else {
                    parameters[i] = EMPTY_INTERCEPTOR;
                }
            } else {
                parameters[i] = InjectorService.get(parameterType);
            }
        }

        try {
            return constructor.newInstance(parameters);
        } catch (InstantiationException e) {
            throw new RuntimeException("Constructor " + constructor.toString() + " cannot be instantiated", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access " + constructor.toString(), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking constructor " + constructor.toString(), e);
        }
    }

    @Override
    public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (!INTERCEPTORS_MUTED.get()) {
            first.onDelete(entity, id, state, propertyNames, types);
        } else {
            super.onDelete(entity, id, state, propertyNames, types);
        }
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        if (!INTERCEPTORS_MUTED.get()) {
            return first.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
        } else {
            return super.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
        }
    }

    @Override
    public boolean onLoad(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (!INTERCEPTORS_MUTED.get()) {
            return first.onLoad(entity, id, state, propertyNames, types);
        } else {
            return super.onLoad(entity, id, state, propertyNames, types);
        }
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (!INTERCEPTORS_MUTED.get()) {
            return first.onSave(entity, id, state, propertyNames, types);
        } else {
            return super.onSave(entity, id, state, propertyNames, types);
        }
    }

    @Override
    public void postFlush(Iterator entities) {
        if (!INTERCEPTORS_MUTED.get()) {
            first.postFlush(entities);
        } else {
            super.postFlush(entities);
        }
    }

    @Override
    public void preFlush(Iterator entities) {
        if (!INTERCEPTORS_MUTED.get()) {
            first.preFlush(entities);
        } else {
            super.preFlush(entities);
        }
    }

    @Override
    public Boolean isTransient(Object entity) {
        if (!INTERCEPTORS_MUTED.get()) {
            return first.isTransient(entity);
        } else {
            return super.isTransient(entity);
        }
    }

    @Deprecated
    @Override
    public int[] findDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        if (!INTERCEPTORS_MUTED.get()) {
            return first.findDirty(entity, id, currentState, previousState, propertyNames, types);
        } else {
            return super.findDirty(entity, id, currentState, previousState, propertyNames, types);
        }
    }

    @Override
    public String getEntityName(Object object) {
        if (!INTERCEPTORS_MUTED.get()) {
            return first.getEntityName(object);
        } else {
            return super.getEntityName(object);
        }
    }

    @Deprecated
    @Override
    public Object getEntity(String entityName, Serializable id) {
        if (!INTERCEPTORS_MUTED.get()) {
            return first.getEntity(entityName, id);
        } else {
            return super.getEntity(entityName, id);
        }
    }

    @Override
    public void afterTransactionBegin(Transaction tx) {
        if (!INTERCEPTORS_MUTED.get()) {
            first.afterTransactionBegin(tx);
        } else {
            super.afterTransactionBegin(tx);
        }
    }

    @Override
    public void afterTransactionCompletion(Transaction tx) {
        if (!INTERCEPTORS_MUTED.get()) {
            first.afterTransactionCompletion(tx);
        } else {
            super.afterTransactionCompletion(tx);
        }
    }

    @Override
    public void beforeTransactionCompletion(Transaction tx) {
        if (!INTERCEPTORS_MUTED.get()) {
            first.beforeTransactionCompletion(tx);
        } else {
            super.beforeTransactionCompletion(tx);
        }
    }

    @Deprecated
    @Override
    public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
        if (!INTERCEPTORS_MUTED.get()) {
            first.onCollectionRemove(collection, key);
        } else {
            super.onCollectionRemove(collection, key);
        }
    }

    @Deprecated
    @Override
    public void onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
        if (!INTERCEPTORS_MUTED.get()) {
            first.onCollectionRecreate(collection, key);
        } else {
            super.onCollectionRecreate(collection, key);
        }
    }

    @Deprecated
    @Override
    public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
        if (!INTERCEPTORS_MUTED.get()) {
            first.onCollectionUpdate(collection, key);
        } else {
            super.onCollectionUpdate(collection, key);
        }
    }

}
