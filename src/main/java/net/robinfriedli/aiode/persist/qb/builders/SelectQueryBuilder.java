package net.robinfriedli.aiode.persist.qb.builders;

import java.util.List;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import net.robinfriedli.aiode.persist.qb.BaseQueryBuilder;
import net.robinfriedli.aiode.persist.qb.PredicateBuilder;
import net.robinfriedli.aiode.persist.qb.QueryConsumer;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptorRegistry;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Query builder implementation for root / non sub queries that select several columns or aggregate functions.
 *
 * @param <E> type of selected entity model
 */
public class SelectQueryBuilder<E> extends BaseQueryBuilder<E, Object[], SelectQueryBuilder<E>> {

    private final List<BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>>> selectionFuncs;

    public SelectQueryBuilder(Class<E> entityClass, QueryInterceptor[] interceptors, List<BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>>> selectionFuncs) {
        super(entityClass, interceptors);
        this.selectionFuncs = selectionFuncs;
    }

    // constructor intended for forking
    protected SelectQueryBuilder(Class<E> entityClass,
                                 QueryInterceptorRegistry queryInterceptorRegistry,
                                 PredicateBuilder predicateBuilder,
                                 List<QueryConsumer<CriteriaQuery<Object[]>>> queryConsumers,
                                 List<BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>>> selectionFuncs) {
        super(entityClass, queryInterceptorRegistry, predicateBuilder, queryConsumers);
        this.selectionFuncs = selectionFuncs;
    }

    @Override
    public SelectQueryBuilder<E> fork() {
        return new SelectQueryBuilder<>(getEntityClass(), getInterceptorRegistry(), getPredicateBuilder(), getQueryConsumers(), selectionFuncs);
    }

    @Override
    protected CriteriaQuery<Object[]> prepare(Session session) {
        return session.getCriteriaBuilder().createQuery(Object[].class);
    }

    @Override
    protected Query<Object[]> finalizeQuery(Session session, CriteriaQuery<Object[]> query, Root<?> root) {
        Selection<?>[] selections = selectionFuncs.stream().map(func -> func.apply(root, session.getCriteriaBuilder())).toArray(Selection<?>[]::new);
        query.multiselect(selections);
        return session.createQuery(query);
    }

    @Override
    protected SelectQueryBuilder<E> self() {
        return this;
    }

}
