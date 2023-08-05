package net.robinfriedli.aiode.persist.qb.builders.sub;

import java.util.List;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import net.robinfriedli.aiode.persist.qb.AbstractQueryBuilder;
import net.robinfriedli.aiode.persist.qb.PredicateBuilder;
import net.robinfriedli.aiode.persist.qb.QueryBuilder;
import net.robinfriedli.aiode.persist.qb.QueryConsumer;
import net.robinfriedli.aiode.persist.qb.SubQueryBuilderFactory;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptorRegistry;
import org.hibernate.Session;

/**
 * Builder for uncorrelated sub queries that select the result of the provided selectionFunction from the provided
 * entity model class.
 *
 * @param <E> type of the selected entity model
 * @param <R> return type of the query, i.e. type of the selected column or aggregate function
 */
public class UncorrelatedSubQueryBuilder<E, R> extends AbstractQueryBuilder<E, Subquery<R>, Subquery<R>, UncorrelatedSubQueryBuilder<E, R>> {

    private final Class<R> returnType;
    private final QueryBuilder<?, ?, ?, ?> superQuery;
    private final BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction;

    public UncorrelatedSubQueryBuilder(Class<R> returnType,
                                       QueryBuilder<?, ?, ?, ?> superQuery,
                                       BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction,
                                       Class<E> entityClass,
                                       QueryInterceptor[] interceptors) {
        super(entityClass, interceptors);
        this.returnType = returnType;
        this.superQuery = superQuery;
        this.selectionFunction = selectionFunction;
    }

    // constructor intended for forking
    protected UncorrelatedSubQueryBuilder(Class<E> entityClass,
                                          QueryInterceptorRegistry queryInterceptorRegistry,
                                          PredicateBuilder predicateBuilder,
                                          List<QueryConsumer<Subquery<R>>> queryConsumers,
                                          Class<R> returnType,
                                          QueryBuilder<?, ?, ?, ?> superQuery,
                                          BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction) {
        super(entityClass, queryInterceptorRegistry, predicateBuilder, queryConsumers);
        this.returnType = returnType;
        this.superQuery = superQuery;
        this.selectionFunction = selectionFunction;
    }

    @Override
    public UncorrelatedSubQueryBuilder<E, R> fork() {
        return new UncorrelatedSubQueryBuilder<>(getEntityClass(), getInterceptorRegistry(), getPredicateBuilder(), getQueryConsumers(), returnType, superQuery, selectionFunction);
    }

    @Override
    protected Subquery<R> doBuild(Session session) {
        Subquery<R> subquery = unwrap(session);

        Root<E> root = subquery.from(getEntityClass());

        CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
        SubQueryBuilderFactory subQueryBuilderFactory = new SubQueryBuilderFactory(this, root);
        subquery.select(selectionFunction.apply(root, criteriaBuilder));

        PredicateBuilder predicateBuilder = getPredicateBuilder();
        if (predicateBuilder != null) {
            subquery.where(predicateBuilder.build(criteriaBuilder, root, subQueryBuilderFactory));
        }

        getQueryConsumers().forEach(queryConsumer -> queryConsumer.accept(root, criteriaBuilder, subquery));

        return subquery;
    }

    @Override
    protected Subquery<R> prepare(Session session) {
        AbstractQuery<?> jpaSuperQuery = superQuery.unwrap(session);
        return jpaSuperQuery.subquery(returnType);
    }

    @Override
    protected UncorrelatedSubQueryBuilder<E, R> self() {
        return this;
    }
}
