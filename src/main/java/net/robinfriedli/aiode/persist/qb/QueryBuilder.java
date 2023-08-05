package net.robinfriedli.aiode.persist.qb;

import java.util.List;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import net.robinfriedli.aiode.persist.qb.builders.sub.CorrelatedSubQueryBuilder;
import net.robinfriedli.aiode.persist.qb.builders.sub.UncorrelatedSubQueryBuilder;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptorRegistry;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Interface for QueryBuilders that serve as wrappers to improve writing JPA CriteriaQueries and add additional features
 * such as forking and {@link QueryInterceptor}.
 *
 * @param <E> the entity type for the root selection
 * @param <R> return type of the {@link #build(Session)} method, usually returns a finished {@link Query} or {@link Subquery}
 * @param <Q> the type of wrapped query builder this builder operates upon, usually {@link CriteriaQuery} or {@link Subquery}
 * @param <B> the type of the current QueryBuilder implementation for method chaining
 */
public interface QueryBuilder<E, R, Q extends AbstractQuery<?>, B extends QueryBuilder<E, R, Q, B>> {

    /***
     * @return The root entity model class the current query root is based on from which either the complete entity or
     * columns thereof are to be fetched. For {@link BaseQueryBuilder} implementations, i.e. root queries and
     * {@link UncorrelatedSubQueryBuilder} this will simply return the class passed to the query builder factory. For
     * {@link CorrelatedSubQueryBuilder} the target class is inferred by the type of the relationAttribute field or, for
     * collections, by trying to resolve the parameterized type of the collection field, returning Object.class if no
     * type parameter is specified.
     */
    Class<E> getEntityClass();

    /**
     * Builds the query to a finished JPA query that can be executed, for main queries this returns a {@link Query}, for
     * sub queries {@link Subquery}.
     *
     * @param session the hibernate session used to create the query
     * @return the built query.
     */
    R build(Session session);

    /**
     * Returns the underlying JPA {@link CriteriaQuery} or {@link Subquery} instance. If this method is called before
     * {@link #build(Session)} this creates the corresponding query and makes sure the same instance will be used by build.
     *
     * @param session hibernate session used to create the query if not prepared before
     * @return the underlying JPA {@link CriteriaQuery} or {@link Subquery} instance
     */
    Q unwrap(Session session);

    /**
     * Append a where condition to this query. If there already is a PredicateBuilder set on the current query builder
     * this will use {@link PredicateBuilder#combine(PredicateBuilder)} to return a new predicate builder that combines
     * the two with an and condition and set the new predicate builder on the query builder.
     *
     * @param predicateBuilder the PredicateBuilder to append
     * @return the current builder
     */
    B where(PredicateBuilder predicateBuilder);

    /**
     * Overload for {@link #where(PredicateBuilder)} with a simplified PredicateBuilder with less parameters that suffice
     * in most cases.
     */
    default B where(SimplePredicateBuilder predicateBuilder) {
        return where((PredicateBuilder) predicateBuilder);
    }

    /**
     * Add manually instantiated interceptors, this replaces interceptor instances of the same type already present on
     * this builder. This also applies to forks and sub queries created after this method is called.
     *
     * @param interceptors interceptors to add
     * @return the current builder
     */
    B addInterceptors(QueryInterceptor... interceptors);

    /**
     * Disables interceptors registered on this query builder. Either disables all interceptor instances of the provided
     * classes or, if none provided, disables all interceptors.
     *
     * @param interceptorClasses the interceptors to disable, or if none provided, all interceptors
     * @return the current builder
     */
    B skipInterceptors(Class<?>... interceptorClasses);

    /**
     * Apply a custom consumer to the underlying JPA query. Used internally for functions like orderBy, groupBy or having.
     *
     * @param consumer the consumer to apply to the underlying query
     * @return the current builder
     */
    B applyToQuery(QueryConsumer<Q> consumer);

    /**
     * Apply a group by to the underlying query, grouping by the resulting expression of applying the provided function.
     *
     * @param groupByFunction function that supplies the expression to group by
     * @return the current builder
     */
    B groupBy(BiFunction<From<?, ?>, CriteriaBuilder, Expression<?>> groupByFunction);

    /**
     * Apply a group by to the underlying query, grouping by the resulting expressions of applying the provided function
     * in the order they are contained in the list.
     *
     * @param groupByFunction function that supplies the expressions to group by
     * @return the current builder
     */
    B groupBySeveral(BiFunction<From<?, ?>, CriteriaBuilder, List<Expression<?>>> groupByFunction);

    /**
     * Apply a having to the underlying query, using the resulting predicate of applying the provided function.
     *
     * @param havingFunction function that supplies the predicate
     * @return the current builder
     */
    B having(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> havingFunction);

    /**
     * Apply a having to the underlying query, using the resulting predicates of applying the provided function in the
     * order the are contained in the array.
     *
     * @param havingFunction function that supplies the predicate array
     * @return the current builder
     */
    B havingSeveral(BiFunction<From<?, ?>, CriteriaBuilder, Predicate[]> havingFunction);

    /**
     * Get the interceptor instances registered on this builder.
     *
     * @param omitSkipped if true, only interceptors that are active, i.e. haven't been disabled by
     *                    {@link #skipInterceptors(Class[])}, will be returned
     * @return the current builder
     */
    QueryInterceptor[] getInterceptors(boolean omitSkipped);

    /**
     * @return the {@link QueryInterceptorRegistry} instance managing interceptors on this builder. This represents a
     * mutable state of the current registry that allows to add and enable / disable interceptors.
     */
    QueryInterceptorRegistry getInterceptorRegistry();

    /**
     * @return a new instance based on the current builder. This new query builder will receive the current predicate builder
     * and thus the current conditions of this builder, a copy of the current state of the interceptor registry and all
     * QueryConsumers added to this builder.
     */
    B fork();

}
