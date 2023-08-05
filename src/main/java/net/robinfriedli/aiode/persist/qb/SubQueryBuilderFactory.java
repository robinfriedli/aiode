package net.robinfriedli.aiode.persist.qb;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import net.robinfriedli.aiode.persist.qb.builders.sub.CorrelatedSubQueryBuilder;
import net.robinfriedli.aiode.persist.qb.builders.sub.UncorrelatedSubQueryBuilder;

/**
 * Factory class that builds sub queries for the current query. An instance of this class is supplied to the
 * {@link PredicateBuilder}.
 */
public class SubQueryBuilderFactory {

    private final QueryBuilder<?, ?, ?, ?> superQuery;
    private final From<?, ?> superRoot;

    public SubQueryBuilderFactory(QueryBuilder<?, ?, ?, ?> superQuery, From<?, ?> superRoot) {
        this.superQuery = superQuery;
        this.superRoot = superRoot;
    }

    /**
     * Create a sub query related to the current query via the provided relationAttribute field on the entity model of the
     * current query.
     *
     * @param relationAttribute the field on the current entity model that references the target entity model
     * @param selectionFunction function returning the selection, selecting a column or aggregate function
     * @param returnType        return type of the sub query, i.e. type of the selected column or aggregate function
     * @param <R>               return type
     * @return the created sub query builder
     */
    public <R> CorrelatedSubQueryBuilder<?, R> createSubQuery(String relationAttribute, BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction, Class<R> returnType) {
        Class<?> rootEntityType = superQuery.getEntityClass();
        try {
            Field attribute = rootEntityType.getDeclaredField(relationAttribute);
            Class<?> type = attribute.getType();
            if (Collection.class.isAssignableFrom(type)) {
                type = tryDetermineTypeParamForCollection(attribute);
            }
            return new CorrelatedSubQueryBuilder<>(returnType, superRoot, superQuery, selectionFunction, relationAttribute, type, superQuery.getInterceptors(false));
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(String.format("No such field '%s' on class '%s'", relationAttribute, rootEntityType));
        }
    }

    /**
     * Overload for {@link #createSubQuery(String, BiFunction, Class)} that simply selects a column.
     */
    public <R> CorrelatedSubQueryBuilder<?, R> createSubQuery(String relationAttribute, String column, Class<R> returnType) {
        return createSubQuery(relationAttribute, (from, criteriaBuilder) -> from.get(column), returnType);
    }

    /**
     * Overload for {@link #createSubQuery(String, String, Class)} that defaults to returning an Object.
     */
    public CorrelatedSubQueryBuilder<?, Object> createSubQuery(String relationAttribute, String column) {
        return createSubQuery(relationAttribute, column, Object.class);
    }

    /**
     * Create an uncorrelated sub query that may select a column from the specified entity model with no relation to
     * the entity model of the current query. The selection in created by applying the provided function.
     *
     * @param entityClass       the target entity model
     * @param selectionFunction the function returning the expression to select, either simply a column or aggregate function
     * @param returnType        the return type of the query, i.e. the type of the column or aggregate function
     * @param <E>               type of the target entity model
     * @param <R>               return type of the query
     * @return the created sub query builder
     */
    public <E, R> UncorrelatedSubQueryBuilder<E, R> createUncorrelatedSubQuery(Class<E> entityClass, BiFunction<From<?, ?>, CriteriaBuilder, Expression<R>> selectionFunction, Class<R> returnType) {
        return new UncorrelatedSubQueryBuilder<>(returnType, superQuery, selectionFunction, entityClass, superQuery.getInterceptors(false));
    }

    /**
     * Overload for {@link #createUncorrelatedSubQuery(Class, BiFunction, Class)} that simply selects a column.
     */
    public <E, R> UncorrelatedSubQueryBuilder<E, R> createUncorrelatedSubQuery(Class<E> entityClass, String selectionColumn, Class<R> returnType) {
        return createUncorrelatedSubQuery(entityClass, (from, cb) -> from.get(selectionColumn), returnType);
    }

    /**
     * Overload for {@link #createUncorrelatedSubQuery(Class, String, Class)} that defaults to returning an Object.
     */
    public <E> UncorrelatedSubQueryBuilder<E, Object> createUncorrelatedSubQuery(Class<E> entityClass, String selectionColumn) {
        return createUncorrelatedSubQuery(entityClass, selectionColumn, Object.class);
    }

    private Class<?> tryDetermineTypeParamForCollection(Field attribute) {
        Type genericType = attribute.getGenericType();
        if (genericType instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                Type parameterizedType = actualTypeArguments[0];

                if (parameterizedType instanceof Class) {
                    return (Class<?>) parameterizedType;
                }
            }
        }

        return Object.class;
    }

}
