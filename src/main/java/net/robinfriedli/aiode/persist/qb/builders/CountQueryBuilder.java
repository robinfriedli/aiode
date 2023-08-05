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

public class CountQueryBuilder<E> extends BaseQueryBuilder<E, Long, CountQueryBuilder<E>> {

    public CountQueryBuilder(Class<E> entityClass, QueryInterceptor[] interceptors) {
        super(entityClass, interceptors);
    }

    // constructor intended for forking
    public CountQueryBuilder(Class<E> entityClass, QueryInterceptorRegistry queryInterceptorRegistry, PredicateBuilder predicateBuilder, List<QueryConsumer<CriteriaQuery<Long>>> queryConsumers) {
        super(entityClass, queryInterceptorRegistry, predicateBuilder, queryConsumers);
    }

    @Override
    protected CriteriaQuery<Long> prepare(Session session) {
        return session.getCriteriaBuilder().createQuery(Long.class);
    }

    @Override
    protected CountQueryBuilder<E> self() {
        return this;
    }

    @Override
    protected Query<Long> finalizeQuery(Session session, CriteriaQuery<Long> query, Root<?> root) {
        query.select(session.getCriteriaBuilder().count(root));
        return session.createQuery(query);
    }

    @Override
    public CountQueryBuilder<E> fork() {
        return new CountQueryBuilder<>(getEntityClass(), getInterceptorRegistry(), getPredicateBuilder(), getQueryConsumers());
    }
}
