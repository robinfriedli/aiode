package net.robinfriedli.botify.persist.qb.builders;

import java.util.List;
import java.util.function.BiFunction;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;

import net.robinfriedli.botify.persist.qb.BaseQueryBuilder;
import net.robinfriedli.botify.persist.qb.PredicateBuilder;
import net.robinfriedli.botify.persist.qb.QueryConsumer;
import net.robinfriedli.botify.persist.qb.interceptor.QueryInterceptor;
import net.robinfriedli.botify.persist.qb.interceptor.QueryInterceptorRegistry;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Query builder implementation for root / non sub queries that select a single column or aggregate function.
 *
 * @param <E> type of selected entity model
 * @param <R> return type of the query, i.e. type of selected column or function
 */
public class SingleSelectQueryBuilder<E, R> extends BaseQueryBuilder<E, R, SingleSelectQueryBuilder<E, R>> {

    private final BiFunction<From<?, ?>, CriteriaBuilder, Selection<? extends R>> selectionFunc;
    private final Class<R> returnType;

    public SingleSelectQueryBuilder(Class<E> entityClass, QueryInterceptor[] interceptors, BiFunction<From<?, ?>, CriteriaBuilder, Selection<? extends R>> selectionFunc, Class<R> returnType) {
        super(entityClass, interceptors);
        this.selectionFunc = selectionFunc;
        this.returnType = returnType;
    }

    // constructor intended for forking
    protected SingleSelectQueryBuilder(Class<E> entityClass,
                                       QueryInterceptorRegistry queryInterceptorRegistry,
                                       PredicateBuilder predicateBuilder,
                                       List<QueryConsumer<CriteriaQuery<R>>> queryConsumers,
                                       BiFunction<From<?, ?>, CriteriaBuilder, Selection<? extends R>> selectionFunc,
                                       Class<R> returnType) {
        super(entityClass, queryInterceptorRegistry, predicateBuilder, queryConsumers);
        this.selectionFunc = selectionFunc;
        this.returnType = returnType;
    }

    @Override
    public SingleSelectQueryBuilder<E, R> fork() {
        return new SingleSelectQueryBuilder<>(getEntityClass(), getInterceptorRegistry(), getPredicateBuilder(), getQueryConsumers(), selectionFunc, returnType);
    }

    @Override
    protected CriteriaQuery<R> prepare(Session session) {
        return session.getCriteriaBuilder().createQuery(returnType);
    }

    @Override
    protected Query<R> finalizeQuery(Session session, CriteriaQuery<R> query, Root<?> root) {
        query.select(selectionFunc.apply(root, session.getCriteriaBuilder()));
        return session.createQuery(query);
    }

    @Override
    protected SingleSelectQueryBuilder<E, R> self() {
        return this;
    }

}
