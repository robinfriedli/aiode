package net.robinfriedli.aiode.persist.qb;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;

/**
 * Simplification of {@link PredicateBuilder} with only the essential parameters.
 */
@FunctionalInterface
public interface SimplePredicateBuilder extends PredicateBuilder {

    Predicate build(CriteriaBuilder cb, From<?, ?> root);

    @Override
    default Predicate build(CriteriaBuilder cb, From<?, ?> root, SubQueryBuilderFactory subQueryFactory) {
        return build(cb, root);
    }

}
