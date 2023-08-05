package net.robinfriedli.aiode.persist.qb.builders;

import java.util.List;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import net.robinfriedli.aiode.persist.qb.BaseQueryBuilder;
import net.robinfriedli.aiode.persist.qb.PredicateBuilder;
import net.robinfriedli.aiode.persist.qb.QueryConsumer;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptorRegistry;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Query builder implementation that selects and returns complete entity objects.
 *
 * @param <E> type of entity model
 */
public class EntityQueryBuilder<E> extends BaseQueryBuilder<E, E, EntityQueryBuilder<E>> {

    public EntityQueryBuilder(Class<E> entityClass, QueryInterceptor[] interceptors) {
        super(entityClass, interceptors);
    }

    // constructor intended for forking
    protected EntityQueryBuilder(Class<E> entityClass, QueryInterceptorRegistry queryInterceptorRegistry, PredicateBuilder predicateBuilder, List<QueryConsumer<CriteriaQuery<E>>> queryConsumers) {
        super(entityClass, queryInterceptorRegistry, predicateBuilder, queryConsumers);
    }

    @Override
    public EntityQueryBuilder<E> fork() {
        return new EntityQueryBuilder<>(getEntityClass(), getInterceptorRegistry(), getPredicateBuilder(), getQueryConsumers());
    }

    @Override
    protected CriteriaQuery<E> prepare(Session session) {
        return session.getCriteriaBuilder().createQuery(getEntityClass());
    }

    @Override
    protected Query<E> finalizeQuery(Session session, CriteriaQuery<E> query, Root<?> root) {
        return session.createQuery(query);
    }

    @Override
    protected EntityQueryBuilder<E> self() {
        return this;
    }

}
