package net.robinfriedli.botify.entities;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.persist.qb.builders.SingleSelectQueryBuilder;
import org.hibernate.Session;

/**
 * Entity interface for entities that have a user-specified identifier that needs to be sanitized with a restricted
 * entity count
 */
public interface SanitizedEntity {

    int getMaxEntityCount(SpringPropertiesConfig springPropertiesConfig);

    String getIdentifierPropertyName();

    String getIdentifier();

    void setSanitizedIdentifier(String sanitizedIdentifier);

    default void addCountUnit(List<CountUnit> countUnits, QueryBuilderFactory queryBuilderFactory, SpringPropertiesConfig springPropertiesConfig) {
        if (countUnits.stream().noneMatch(countUnit -> countUnit.getType().equals(getClass()))) {
            int maxEntityCount = getMaxEntityCount(springPropertiesConfig);
            if (maxEntityCount > 0) {
                countUnits.add(createCountUnit(maxEntityCount, queryBuilderFactory));
            }
        }
    }

    default CountUnit createCountUnit(int maxEntityCount, QueryBuilderFactory queryBuilderFactory) {
        return new CountUnit(
            getClass(),
            this,
            session -> queryBuilderFactory.select(getClass(), ((from, cb) -> cb.count(from.get("pk"))), Long.class),
            String.format("Maximum %s count of %s reached", getClass().getSimpleName(), maxEntityCount),
            maxEntityCount
        );
    }

    default Set<IdentifierFormattingRule> getIdentifierFormattingRules() {
        return Collections.emptySet();
    }


    /**
     * Defines a task to count entities per transaction. There's usually one instance per sanitized entity type
     * created this transaction. But, for instance, for StoredScript entities there is one separate task per script usage.
     */
    class CountUnit {

        private final Class<? extends SanitizedEntity> type;
        private final SanitizedEntity entity;
        private final Function<Session, SingleSelectQueryBuilder<? extends SanitizedEntity, Long>> countQueryProducer;
        private final String errorMessage;
        private final int maxEntityCount;

        public CountUnit(Class<? extends SanitizedEntity> type, SanitizedEntity entity, Function<Session, SingleSelectQueryBuilder<? extends SanitizedEntity, Long>> countQueryProducer, String errorMessage, int maxEntityCount) {
            this.type = type;
            this.entity = entity;
            this.countQueryProducer = countQueryProducer;
            this.errorMessage = errorMessage;
            this.maxEntityCount = maxEntityCount;
        }

        public Class<? extends SanitizedEntity> getType() {
            return type;
        }

        public SanitizedEntity getEntity() {
            return entity;
        }

        public Function<Session, SingleSelectQueryBuilder<? extends SanitizedEntity, Long>> getCountQueryProducer() {
            return countQueryProducer;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getMaxEntityCount() {
            return maxEntityCount;
        }
    }

    /**
     * Defines a condition that is checked for the identifier when saving an instance of this entity. If the predicate
     * fails the provided errorMessage is sent and the transaction fails, the errorMessage string receives the actual
     * identifier as parameter for formatting.
     */
    class IdentifierFormattingRule {

        private final String errorMessage;
        private final Predicate<String> predicate;

        public IdentifierFormattingRule(String errorMessage, Predicate<String> predicate) {
            this.errorMessage = errorMessage;
            this.predicate = predicate;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Predicate<String> getPredicate() {
            return predicate;
        }
    }

}
