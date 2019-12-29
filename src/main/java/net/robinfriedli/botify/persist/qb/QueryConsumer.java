package net.robinfriedli.botify.persist.qb;

import javax.persistence.criteria.AbstractQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Subquery;

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
