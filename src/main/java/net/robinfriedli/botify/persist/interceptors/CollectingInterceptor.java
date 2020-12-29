package net.robinfriedli.botify.persist.interceptors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.api.client.util.Lists;
import com.google.common.collect.Iterables;
import net.robinfriedli.botify.function.CheckedConsumer;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

/**
 * Interceptor extension that records all created, deleted and updated entities during a transaction
 */
public abstract class CollectingInterceptor extends ChainableInterceptor {

    private final List<Object> createdEntities = Lists.newArrayList();
    private final List<Object> deletedEntities = Lists.newArrayList();
    private final List<Object> updatedEntities = Lists.newArrayList();
    private final Map<Object, Map<String, Object>> originalEntityStates = new HashMap<>();

    public CollectingInterceptor(Interceptor next, Logger logger) {
        super(next, logger);
    }

    public abstract void afterCommit() throws Exception;

    @Override
    public void onSaveChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        createdEntities.add(entity);
    }

    @Override
    public void onDeleteChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        deletedEntities.add(entity);
    }

    @Override
    public void onFlushDirtyChained(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        updatedEntities.add(entity);

        Map<String, Object> originalState = originalEntityStates.computeIfAbsent(entity, k -> new HashMap<>());

        for (int i = 0; i < propertyNames.length; i++) {
            String property = propertyNames[i];

            if (originalState.containsKey(property)) {
                continue;
            }

            Object prevState = previousState[i];
            if (!Objects.equals(prevState, currentState[i])) {
                originalState.put(property, prevState);
            }
        }
    }

    @Override
    public void afterTransactionCompletionChained(Transaction tx) {
        try {
            if (!tx.getRollbackOnly()) {
                StaticSessionProvider.consumeSessionWithoutInterceptors((CheckedConsumer<Session>) session -> afterCommit());
            }
        } finally {
            clearState();
        }
    }

    public List<Object> getCreatedEntities() {
        return createdEntities;
    }

    public <E> List<E> getCreatedEntities(Class<E> type) {
        return createdEntities.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
    }

    public List<Object> getDeletedEntities() {
        return deletedEntities;
    }

    public <E> List<E> getDeletedEntities(Class<E> type) {
        return deletedEntities.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
    }

    public List<Object> getUpdatedEntities() {
        return updatedEntities;
    }

    public <E> List<E> getUpdatedEntities(Class<E> type) {
        return updatedEntities.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
    }

    public List<Object> getAffectedEntites() {
        Iterable<Object> concat = Iterables.concat(createdEntities, deletedEntities, updatedEntities);

        return StreamSupport.stream(concat.spliterator(), false).collect(Collectors.toList());
    }

    public <E> List<E> getAffectedEntities(Class<E> type) {
        Iterable<Object> concat = Iterables.concat(createdEntities, deletedEntities, updatedEntities);

        return StreamSupport.stream(concat.spliterator(), false)
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toList());
    }

    /**
     * Check whether the field with the provided name of the provided entity has ever been updated in the current
     * transaction. This does not mean the current value of the field is different from it's original value as the field
     * might have been set back to its original value in the same transaction.
     *
     * @param entity the entity check
     * @param field  the name of the field
     * @return true if the field has been updated
     */
    public boolean isFieldTouched(Object entity, String field) {
        Map<String, Object> originalState = originalEntityStates.get(entity);

        if (originalState != null) {
            return originalState.containsKey(field);
        }

        return false;
    }

    /**
     * Get the original value of a field that has been touched in the current transaction. This corresponds to the previous
     * value of the field recorded during its first update in the current transaction. If this field has not been touched
     * in the current transaction this returns null (see {@link #isFieldTouched(Object, String)}).
     *
     * @param entity the entity for which to retrieve the previous value of the provided field
     * @param field  the name of the field
     * @return the original value recorded during the first update to the field in the current transaction, null if the
     * field has never been touched
     */
    @Nullable
    public Object getOriginalValue(Object entity, String field) {
        Map<String, Object> originalState = originalEntityStates.get(entity);

        if (originalState != null) {
            return originalState.get(field);
        }

        return null;
    }

    protected void clearState() {
        createdEntities.clear();
        deletedEntities.clear();
        updatedEntities.clear();
        originalEntityStates.clear();
    }

}
