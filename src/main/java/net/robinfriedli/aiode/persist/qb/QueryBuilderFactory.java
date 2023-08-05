package net.robinfriedli.aiode.persist.qb;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Selection;
import net.robinfriedli.aiode.persist.qb.builders.CountQueryBuilder;
import net.robinfriedli.aiode.persist.qb.builders.EntityQueryBuilder;
import net.robinfriedli.aiode.persist.qb.builders.SelectQueryBuilder;
import net.robinfriedli.aiode.persist.qb.builders.SingleSelectQueryBuilder;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import org.springframework.stereotype.Component;

@Component
public final class QueryBuilderFactory {

    private final QueryInterceptor[] interceptors;

    public QueryBuilderFactory(QueryInterceptor... interceptors) {
        this.interceptors = interceptors;
    }

    /**
     * Create an {@link EntityQueryBuilder} selecting the provided entity model.
     *
     * @param entityClass the JPA annotated entity model
     * @param <E>         type of the entity model
     * @return the created query builder
     */
    public final <E> EntityQueryBuilder<E> find(Class<E> entityClass) {
        return new EntityQueryBuilder<>(entityClass, interceptors);
    }

    /**
     * Create a {@link SingleSelectQueryBuilder} selecting the provided entity model and selecting the provided column.
     *
     * @param entityClass the JPA annotated entity model
     * @param column      the column to select
     * @param returnType  type of the selected column
     * @param <E>         type of the entity model
     * @param <R>         return type of the query, i.e. type of the column
     * @return the created entity builder
     */
    public final <E, R> SingleSelectQueryBuilder<E, R> select(Class<E> entityClass, String column, Class<R> returnType) {
        return new SingleSelectQueryBuilder<>(entityClass, interceptors, (from, cb) -> from.get(column), returnType);
    }

    /**
     * Create a {@link SingleSelectQueryBuilder} selecting the provided entity model and applying the provided selection
     * function. This allows to select an aggregate function, e.g. count, instead of simply a column.
     *
     * @param entityClass       the JPA annotated entity model
     * @param selectionFunction the function that returns the selection
     * @param returnType        type the selection returns, either type of the selected column or aggregate function (e.g. Long for count)
     * @param <E>               type of the entity model
     * @param <R>               return type of the query, i.e. type of the selection
     * @return the created query builder
     */
    public final <E, R> SingleSelectQueryBuilder<E, R> select(Class<E> entityClass, BiFunction<From<?, ?>, CriteriaBuilder, Selection<? extends R>> selectionFunction, Class<R> returnType) {
        return new SingleSelectQueryBuilder<>(entityClass, interceptors, selectionFunction, returnType);
    }

    /**
     * Overload for {@link #select(Class, String, Class)} that defaults to returning an Object.
     */
    public final <E> SingleSelectQueryBuilder<E, Object> select(Class<E> entityClass, String column) {
        return new SingleSelectQueryBuilder<>(entityClass, interceptors, (from, cb) -> from.get(column), Object.class);
    }

    /**
     * Overload for {@link #select(Class, BiFunction, Class)} that defaults to returning an Object.
     */
    public final <E> SingleSelectQueryBuilder<E, Object> select(Class<E> entityClass, BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>> selectionFunction) {
        return new SingleSelectQueryBuilder<>(entityClass, interceptors, selectionFunction, Object.class);
    }

    /**
     * Create a {@link SelectQueryBuilder} selecting the provided entity model and multi-selecting the provided columns.
     *
     * @param entityClass the JPA annotated entity model
     * @param columns     the columns to select
     * @param <E>         type of the entity model
     * @return the created entity builder
     */
    public final <E> SelectQueryBuilder<E> select(Class<E> entityClass, String... columns) {
        List<BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>>> functions = Arrays.stream(columns)
            .map(col -> (BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>>) (from, cb) -> from.get(col))
            .collect(Collectors.toList());
        return new SelectQueryBuilder<>(entityClass, interceptors, functions);
    }

    /**
     * Create a {@link SelectQueryBuilder} selecting the provided entity model and multi-selecting the results of the
     * provided selection functions in the order they are contained in the array. This allows to select an aggregate
     * function, e.g. count, instead of simply a column.
     *
     * @param entityClass    the JPA annotated entity model
     * @param selectionFuncs the functions creating a selection
     * @param <E>            type of the entity model
     * @return the created entity builder
     */
    @SafeVarargs
    public final <E> SelectQueryBuilder<E> select(Class<E> entityClass, BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>>... selectionFuncs) {
        return new SelectQueryBuilder<>(entityClass, interceptors, Arrays.asList(selectionFuncs));
    }

    /**
     * Overload for {@link #select(Class, BiFunction[])} that accepts a list instead of an array.
     */
    public final <E> SelectQueryBuilder<E> select(Class<E> entityClass, List<BiFunction<From<?, ?>, CriteriaBuilder, Selection<?>>> selectionFuncs) {
        return new SelectQueryBuilder<>(entityClass, interceptors, selectionFuncs);
    }

    public <E> CountQueryBuilder<E> count(Class<E> entityClass) {
        return new CountQueryBuilder<>(entityClass, interceptors);
    }

}
