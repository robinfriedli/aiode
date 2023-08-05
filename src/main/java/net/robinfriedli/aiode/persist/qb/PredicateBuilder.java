package net.robinfriedli.aiode.persist.qb;

import javax.annotation.CheckReturnValue;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;

/**
 * Functional interface that builds a JPA predicate by applying the provided criteria builder and query root.
 */
@FunctionalInterface
public interface PredicateBuilder {

    Predicate build(CriteriaBuilder cb, From<?, ?> root, SubQueryBuilderFactory subQueryFactory);

    @CheckReturnValue
    default PredicateBuilder combine(PredicateBuilder other) {
        return (cb, root, subQueryFactory) -> cb.and(this.build(cb, root, subQueryFactory), other.build(cb, root, subQueryFactory));
    }

}
