package net.robinfriedli.aiode.persist.qb;

import java.util.List;
import java.util.function.BiFunction;

import com.google.common.collect.Lists;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptorRegistry;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Abstract query builder for common code of all query builder types, including sub queries.
 *
 * @param <E> the type of entity model
 * @param <R> return type of the build method, i.e. the finished query type this builder generates
 * @param <Q> type of the underlying query ({@link Query} or {@link Subquery})
 * @param <B>
 */
public abstract class AbstractQueryBuilder<E, R, Q extends AbstractQuery<?>, B extends AbstractQueryBuilder<E, R, Q, B>> implements QueryBuilder<E, R, Q, B> {

    private final Class<E> entityClass;
    private final List<QueryConsumer<Q>> queryConsumers = Lists.newArrayList();
    private final QueryInterceptorRegistry interceptorRegistry;

    private PredicateBuilder predicateBuilder;
    private Q jpaQuery;

    protected AbstractQueryBuilder(Class<E> entityClass, QueryInterceptor[] interceptors) {
        this.entityClass = entityClass;
        this.interceptorRegistry = new QueryInterceptorRegistry(interceptors);
    }

    // constructor intended for forking
    protected AbstractQueryBuilder(Class<E> entityClass, QueryInterceptorRegistry queryInterceptorRegistry, PredicateBuilder predicateBuilder, List<QueryConsumer<Q>> queryConsumers) {
        this.entityClass = entityClass;
        this.interceptorRegistry = queryInterceptorRegistry.copy();
        this.predicateBuilder = predicateBuilder;
        this.queryConsumers.addAll(queryConsumers);
    }

    @Override
    public Class<E> getEntityClass() {
        return entityClass;
    }

    @Override
    public R build(Session session) {
        interceptorRegistry.getInterceptors(true)
            .filter(interceptor -> interceptor.shouldIntercept(entityClass))
            .forEach(interceptor -> interceptor.intercept(this));
        return doBuild(session);
    }

    @Override
    public Q unwrap(Session session) {
        if (jpaQuery == null) {
            jpaQuery = prepare(session);
        }

        return jpaQuery;
    }

    @Override
    public B where(PredicateBuilder predicateBuilder) {
        if (this.predicateBuilder == null) {
            this.predicateBuilder = predicateBuilder;
        } else {
            this.predicateBuilder = this.predicateBuilder.combine(predicateBuilder);
        }

        return self();
    }

    @Override
    public B addInterceptors(QueryInterceptor... interceptors) {
        interceptorRegistry.addInterceptors(interceptors);

        return self();
    }

    @Override
    public B skipInterceptors(Class<?>... interceptorClasses) {
        interceptorRegistry.disableInterceptors(interceptorClasses);

        return self();
    }

    @Override
    public B applyToQuery(QueryConsumer<Q> queryConsumer) {
        queryConsumers.add(queryConsumer);
        return self();
    }

    @Override
    public B groupBy(BiFunction<From<?, ?>, CriteriaBuilder, Expression<?>> groupByFunction) {
        return applyToQuery((root, cb, query) -> query.groupBy(groupByFunction.apply(root, cb)));
    }

    @Override
    public B groupBySeveral(BiFunction<From<?, ?>, CriteriaBuilder, List<Expression<?>>> groupByFunction) {
        return applyToQuery((root, cb, query) -> query.groupBy(groupByFunction.apply(root, cb)));
    }

    @Override
    public B having(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> havingFunction) {
        return applyToQuery((root, cb, query) -> query.having(havingFunction.apply(root, cb)));
    }

    @Override
    public B havingSeveral(BiFunction<From<?, ?>, CriteriaBuilder, Predicate[]> havingFunction) {
        return applyToQuery((root, cb, query) -> query.having(havingFunction.apply(root, cb)));
    }

    @Override
    public QueryInterceptor[] getInterceptors(boolean omitSkipped) {
        return interceptorRegistry.getInterceptors(omitSkipped).toArray(QueryInterceptor[]::new);
    }

    @Override
    public QueryInterceptorRegistry getInterceptorRegistry() {
        return interceptorRegistry;
    }

    protected PredicateBuilder getPredicateBuilder() {
        return predicateBuilder;
    }

    protected List<QueryConsumer<Q>> getQueryConsumers() {
        return queryConsumers;
    }

    protected abstract R doBuild(Session session);

    protected abstract Q prepare(Session session);

    protected abstract B self();

}
