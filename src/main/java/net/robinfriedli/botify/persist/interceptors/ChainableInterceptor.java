package net.robinfriedli.botify.persist.interceptors;

import java.io.Serializable;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityMode;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

/**
 * Interceptor extension that enables simple chaining of hibernate interceptors that comes with methods that call the
 * next interceptor in the chain automatically. See {@link InterceptorChain}.
 */
@SuppressWarnings({"unused", "RedundantThrows", "WeakerAccess"})
public class ChainableInterceptor extends EmptyInterceptor {

    private final Interceptor next;
    private final Logger logger;

    public ChainableInterceptor(Interceptor next) {
        this(next, null);
    }

    public ChainableInterceptor(Interceptor next, Logger logger) {
        this.next = next;
        if (logger == null) {
            this.logger = LoggerFactory.getLogger(getClass());
        } else {
            this.logger = logger;
        }
    }

    protected Interceptor next() {
        return next;
    }

    @Override
    public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        try {
            onDeleteChained(entity, id, state, propertyNames, types);
        } catch (Exception e) {
            logger.error("Error in onDelete of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.onDelete(entity, id, state, propertyNames, types);
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        try {
            onFlushDirtyChained(entity, id, currentState, previousState, propertyNames, types);
        } catch (Exception e) {
            logger.error("Error in onFlushDirty of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public boolean onLoad(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        try {
            onLoadChained(entity, id, state, propertyNames, types);
        } catch (Exception e) {
            logger.error("Error in onLoad of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.onLoad(entity, id, state, propertyNames, types);
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        try {
            onSaveChained(entity, id, state, propertyNames, types);
        } catch (Exception e) {
            logger.error("Error in onSave of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.onSave(entity, id, state, propertyNames, types);
    }

    @Override
    public void postFlush(Iterator entities) {
        try {
            postFlushChained(entities);
        } catch (Exception e) {
            logger.error("Error in postFlush of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.postFlush(entities);
    }

    @Override
    public void preFlush(Iterator entities) {
        try {
            preFlushChained(entities);
        } catch (Exception e) {
            logger.error("Error in preFlush of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.preFlush(entities);
    }

    @Override
    public Boolean isTransient(Object entity) {
        try {
            isTransientChained(entity);
        } catch (Exception e) {
            logger.error("Error in isTransient of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.isTransient(entity);
    }

    @Override
    public Object instantiate(String entityName, EntityMode entityMode, Serializable id) {
        try {
            instantiateChained(entityName, entityMode, id);
        } catch (Exception e) {
            logger.error("Error in instantiate of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.instantiate(entityName, entityMode, id);
    }

    @Override
    public int[] findDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        try {
            findDirtyChained(entity, id, currentState, previousState, propertyNames, types);
        } catch (Exception e) {
            logger.error("Error in findDirty of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.findDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public String getEntityName(Object object) {
        try {
            getEntityNameChained(object);
        } catch (Exception e) {
            logger.error("Error in getEntityName of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.getEntityName(object);
    }

    @Override
    public Object getEntity(String entityName, Serializable id) {
        try {
            getEntityChained(entityName, id);
        } catch (Exception e) {
            logger.error("Error in getEntity of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.getEntity(entityName, id);
    }

    @Override
    public void afterTransactionBegin(Transaction tx) {
        try {
            afterTransactionBeginChained(tx);
        } catch (Exception e) {
            logger.error("Error in afterTransactionBegin of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.afterTransactionBegin(tx);
    }

    @Override
    public void afterTransactionCompletion(Transaction tx) {
        try {
            afterTransactionCompletionChained(tx);
        } catch (Exception e) {
            logger.error("Error in afterTransactionCompletion of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.afterTransactionCompletion(tx);
    }

    @Override
    public void beforeTransactionCompletion(Transaction tx) {
        try {
            beforeTransactionCompletionChained(tx);
        } catch (Exception e) {
            logger.error("Error in beforeTransactionCompletion of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.beforeTransactionCompletion(tx);
    }

    @Deprecated
    @Override
    public String onPrepareStatement(String sql) {
        try {
            onPrepareStatementChained(sql);
        } catch (Exception e) {
            logger.error("Error in onPrepareStatement of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        return next.onPrepareStatement(sql);
    }

    @Override
    public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
        try {
            onCollectionRemoveChained(collection, key);
        } catch (Exception e) {
            logger.error("Error in onCollectionRemove of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.onCollectionRemove(collection, key);
    }

    @Override
    public void onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
        try {
            onCollectionRecreateChained(collection, key);
        } catch (Exception e) {
            logger.error("Error in onCollectionRecreate of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.onCollectionRecreate(collection, key);
    }

    @Override
    public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
        try {
            onCollectionUpdateChained(collection, key);
        } catch (Exception e) {
            logger.error("Error in onCollectionUpdate of ChainableInterceptor " + getClass().getSimpleName(), e);
        }
        next.onCollectionUpdate(collection, key);
    }


    /**
     * Void methods that are safe to use in the InterceptorChain that do not interrupt the chain when throwing an
     * exception, but log all errors instead. They are all void as they are only intended to listen to the specific
     * events without intercepting the result. If you do want to interrupt the chain with an exception
     * (e.g. validation or security) or do want intercept the result, override the standard method instead. When you use
     * these methods below you do not need to call the same method of the next interceptor explicitly.
     */

    public void onDeleteChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) throws Exception {
    }

    public void onFlushDirtyChained(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) throws Exception {
    }

    public void onLoadChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) throws Exception {
    }

    public void onSaveChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) throws Exception {
    }

    public void postFlushChained(Iterator entities) throws Exception {
    }

    public void preFlushChained(Iterator entities) throws Exception {
    }

    public void isTransientChained(Object entity) throws Exception {
    }

    public void instantiateChained(String entityName, EntityMode entityMode, Serializable id) throws Exception {
    }

    public void findDirtyChained(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) throws Exception {
    }

    public void getEntityNameChained(Object object) throws Exception {
    }

    public void getEntityChained(String entityName, Serializable id) throws Exception {
    }

    public void afterTransactionBeginChained(Transaction tx) throws Exception {
    }

    public void afterTransactionCompletionChained(Transaction tx) throws Exception {
    }

    public void beforeTransactionCompletionChained(Transaction tx) throws Exception {
    }

    @Deprecated
    public void onPrepareStatementChained(String sql) throws Exception {
    }

    public void onCollectionRemoveChained(Object collection, Serializable key) throws Exception {
    }

    public void onCollectionRecreateChained(Object collection, Serializable key) throws Exception {
    }

    public void onCollectionUpdateChained(Object collection, Serializable key) throws Exception {
    }
}
