package net.robinfriedli.aiode.persist.qb.interceptor;

import net.robinfriedli.aiode.persist.qb.QueryBuilder;

/**
 * Interface for query interceptors that are called when building a {@link QueryBuilder} implementation. Implementations
 * may, for example, filter the query based on current guild context, only returning results owned by the current guild
 * without having to explicitly specify the condition in the query each time.
 */
public interface QueryInterceptor {

    /**
     * Executes the actual intercept logic that modifies the given query builder instance which is currently being built.
     *
     * @param queryBuilder the query builder to extend
     */
    void intercept(QueryBuilder<?, ?, ?, ?> queryBuilder);

    /**
     * Executes a check whether this interceptor should get involved with the current query, based on the selected
     * entity model.
     *
     * @param entityClass the selected entity model
     * @return <code>true</code> if this interceptor should modify queries selecting the provided entity model
     */
    boolean shouldIntercept(Class<?> entityClass);

}
