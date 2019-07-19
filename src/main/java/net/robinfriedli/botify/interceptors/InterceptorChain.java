package net.robinfriedli.botify.interceptors;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Iterator;

import net.robinfriedli.botify.util.Cache;
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityMode;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

public class InterceptorChain extends EmptyInterceptor {

    private final Interceptor first;

    public InterceptorChain(Interceptor first) {
        this.first = first;
    }

    @SafeVarargs
    public static InterceptorChain of(Class<? extends ChainableInterceptor>... interceptors) {
        if (interceptors.length == 0) {
            return new InterceptorChain(EmptyInterceptor.INSTANCE);
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
                    parameters[i] = EmptyInterceptor.INSTANCE;
                }
            } else {
                parameters[i] = Cache.get(parameterType);
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
        first.onDelete(entity, id, state, propertyNames, types);
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        return first.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public boolean onLoad(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        return first.onLoad(entity, id, state, propertyNames, types);
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        return first.onSave(entity, id, state, propertyNames, types);
    }

    @Override
    public void postFlush(Iterator entities) {
        first.postFlush(entities);
    }

    @Override
    public void preFlush(Iterator entities) {
        first.preFlush(entities);
    }

    @Override
    public Boolean isTransient(Object entity) {
        return first.isTransient(entity);
    }

    @Override
    public Object instantiate(String entityName, EntityMode entityMode, Serializable id) {
        return first.instantiate(entityName, entityMode, id);
    }

    @Override
    public int[] findDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        return first.findDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public String getEntityName(Object object) {
        return first.getEntityName(object);
    }

    @Override
    public Object getEntity(String entityName, Serializable id) {
        return first.getEntity(entityName, id);
    }

    @Override
    public void afterTransactionBegin(Transaction tx) {
        first.afterTransactionBegin(tx);
    }

    @Override
    public void afterTransactionCompletion(Transaction tx) {
        first.afterTransactionCompletion(tx);
    }

    @Override
    public void beforeTransactionCompletion(Transaction tx) {
        first.beforeTransactionCompletion(tx);
    }

    @Override
    public String onPrepareStatement(String sql) {
        return first.onPrepareStatement(sql);
    }

    @Override
    public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
        first.onCollectionRemove(collection, key);
    }

    @Override
    public void onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
        first.onCollectionRecreate(collection, key);
    }

    @Override
    public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
        first.onCollectionUpdate(collection, key);
    }

}
