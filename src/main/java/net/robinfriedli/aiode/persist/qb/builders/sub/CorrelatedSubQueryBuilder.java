package net.robinfriedli.aiode.persist.qb.builders.sub;

import java.util.List;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
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
 * Builder for correlated sub queries related to the root of the super query via the provided relationAttribute
 *
 * @param <E> type of the entity model inferred by the type of the relationAttribute field or, for collections, by
 *            trying to resolve the parameterized type of the collection field, returning Object.class if no type
 *            parameter is specified
 * @param <R> return type of the query, i.e type of the selected column
 */
public class CorrelatedSubQueryBuilder<E, R> extends AbstractQueryBuilder<E, Subquery<R>, Subquery<R>, CorrelatedSubQueryBuilder<E, R>> {

    private final Class<R> returnType;
    private final From<?, ?> superRoot;
    private final QueryBuilder<?, ?, ?, ?> superQuery;
    private final BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction;
    private final String relationAttribute;

    public CorrelatedSubQueryBuilder(Class<R> returnType,
                                     From<?, ?> superRoot,
                                     QueryBuilder<?, ?, ?, ?> superQuery,
                                     BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction,
                                     String relationAttribute,
                                     Class<E> entityClass,
                                     QueryInterceptor[] interceptors) {
        super(entityClass, interceptors);
        this.returnType = returnType;
        this.superRoot = superRoot;
        this.superQuery = superQuery;
        this.selectionFunction = selectionFunction;
        this.relationAttribute = relationAttribute;
    }

    // constructor intended for forking
    protected CorrelatedSubQueryBuilder(Class<E> entityClass,
                                        QueryInterceptorRegistry queryInterceptorRegistry,
                                        PredicateBuilder predicateBuilder,
                                        List<QueryConsumer<Subquery<R>>> queryConsumers,
                                        Class<R> returnType,
                                        From<?, ?> superRoot,
                                        QueryBuilder<?, ?, ?, ?> superQuery,
                                        BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction,
                                        String relationAttribute) {
        super(entityClass, queryInterceptorRegistry, predicateBuilder, queryConsumers);
        this.returnType = returnType;
        this.superRoot = superRoot;
        this.superQuery = superQuery;
        this.selectionFunction = selectionFunction;
        this.relationAttribute = relationAttribute;
    }

    @Override
    public CorrelatedSubQueryBuilder<E, R> fork() {
        return new CorrelatedSubQueryBuilder<>(getEntityClass(), getInterceptorRegistry(), getPredicateBuilder(), getQueryConsumers(), returnType, superRoot, superQuery, selectionFunction, relationAttribute);
    }

    @Override
    protected Subquery<R> doBuild(Session session) {
        Subquery<R> subquery = unwrap(session);
        From<?, ?> root;

        if (superRoot instanceof Root<?>) {
            root = subquery.correlate((Root<?>) superRoot).join(relationAttribute);
        } else if (superRoot instanceof Join<?, ?>) {
            root = subquery.correlate((Join<?, ?>) superRoot).join(relationAttribute);
        } else {
            throw new UnsupportedOperationException("Unsupported root type: " + superRoot.getClass());
        }

        CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
        subquery.select(selectionFunction.apply(root, criteriaBuilder));
        SubQueryBuilderFactory subQueryBuilderFactory = new SubQueryBuilderFactory(this, root);

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
    protected CorrelatedSubQueryBuilder<E, R> self() {
        return this;
    }

}
