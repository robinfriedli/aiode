package net.robinfriedli.aiode.persist.qb;

import java.util.List;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptorRegistry;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Abstract query builder for all root query types, i.e. everything that is not a sub query.
 *
 * @param <E> type of the entity model
 * @param <R> return type of the query, equal to the selected entity model type or column type (Object[] for multiselect)
 * @param <B> the type of BaseQueryBuilder implementation used bs #self for method chaining
 */
public abstract class BaseQueryBuilder<E, R, B extends BaseQueryBuilder<E, R, B>> extends AbstractQueryBuilder<E, Query<R>, CriteriaQuery<R>, B> {

    protected BaseQueryBuilder(Class<E> entityClass, QueryInterceptor[] interceptors) {
        super(entityClass, interceptors);
    }

    // constructor intended for forking
    protected BaseQueryBuilder(Class<E> entityClass, QueryInterceptorRegistry queryInterceptorRegistry, PredicateBuilder predicateBuilder, List<QueryConsumer<CriteriaQuery<R>>> queryConsumers) {
        super(entityClass, queryInterceptorRegistry, predicateBuilder, queryConsumers);
    }

    @Override
    public Query<R> doBuild(Session session) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<R> criteriaQuery = prepare(session);
        Root<E> root = criteriaQuery.from(getEntityClass());

        SubQueryBuilderFactory subQueryBuilderFactory = new SubQueryBuilderFactory(this, root);
        PredicateBuilder predicateBuilder = getPredicateBuilder();
        if (predicateBuilder != null) {
            criteriaQuery.where(predicateBuilder.build(cb, root, subQueryBuilderFactory));
        }

        getQueryConsumers().forEach(queryConsumer -> queryConsumer.accept(root, cb, criteriaQuery));

        return finalizeQuery(session, criteriaQuery, root);
    }

    public B orderBy(BiFunction<From<?, ?>, CriteriaBuilder, Order> orderByFunction) {
        return applyToQuery((root, cb, query) -> query.orderBy(orderByFunction.apply(root, cb)));
    }

    public B orderBySeveral(BiFunction<From<?, ?>, CriteriaBuilder, List<Order>> orderByFunction) {
        return applyToQuery((root, cb, query) -> query.orderBy(orderByFunction.apply(root, cb)));
    }

    protected abstract Query<R> finalizeQuery(Session session, CriteriaQuery<R> query, Root<?> root);

}
