package net.robinfriedli.aiode.persist.qb;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Subquery;

/**
 * Custom consumer that accepts a Query root, CriteriaBuilder and parameterized JPA query implementation, either
 * {@link CriteriaQuery} or {@link Subquery}.
 *
 * @param <Q> the type of JPA query implementation
 */
@FunctionalInterface
public interface QueryConsumer<Q extends AbstractQuery<?>> {

    void accept(From<?, ?> root, CriteriaBuilder cb, Q query);

}
