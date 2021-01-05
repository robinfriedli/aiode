package net.robinfriedli.botify.persist.interceptors;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.FlushModeType;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.entities.SanitizedEntity;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.util.Util;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

/**
 * Interceptor that performs additional actions for entities implementing the {@link SanitizedEntity} interface, such as
 * normalizing whitespace in identifiers, checking identifier formatting rules and limiting maximum entity counts.
 */
public class SanitizingEntityInterceptor extends ChainableInterceptor {

    private final List<SanitizedEntity.CountUnit> countUnits = Lists.newArrayList();
    private final QueryBuilderFactory queryBuilderFactory;
    private final SpringPropertiesConfig springPropertiesConfig;

    public SanitizingEntityInterceptor(Interceptor next, QueryBuilderFactory queryBuilderFactory, SpringPropertiesConfig springPropertiesConfig) {
        super(next);
        this.queryBuilderFactory = queryBuilderFactory;
        this.springPropertiesConfig = springPropertiesConfig;
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof SanitizedEntity) {
            SanitizedEntity sanitizedEntity = (SanitizedEntity) entity;
            Set<SanitizedEntity.IdentifierFormattingRule> identifierFormattingRules = sanitizedEntity.getIdentifierFormattingRules();

            for (SanitizedEntity.IdentifierFormattingRule identifierFormattingRule : identifierFormattingRules) {
                String identifier = sanitizedEntity.getIdentifier();
                if (!identifierFormattingRule.getPredicate().test(identifier)) {
                    String errorMessage = String.format(identifierFormattingRule.getErrorMessage(), identifier);
                    throw new InvalidPropertyValueException(errorMessage);
                }
            }
        }
        return super.onSave(entity, id, state, propertyNames, types);
    }

    @Override
    public void onSaveChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof SanitizedEntity) {
            SanitizedEntity sanitizedEntity = (SanitizedEntity) entity;
            sanitizedEntity.addCountUnit(countUnits, queryBuilderFactory, springPropertiesConfig);

            String identifier = sanitizedEntity.getIdentifier();
            String sanitizedIdentifier = Util.normalizeWhiteSpace(identifier);

            if (!identifier.equals(sanitizedIdentifier)) {
                sanitizedEntity.setSanitizedIdentifier(sanitizedIdentifier);
                for (int i = 0; i < state.length; i++) {
                    if (sanitizedEntity.getIdentifierPropertyName().equals(propertyNames[i])) {
                        state[i] = sanitizedIdentifier;
                    }
                }
            }
        }
    }

    @Override
    public void preFlush(Iterator entities) {
        StaticSessionProvider.consumeSession(session -> {
            for (SanitizedEntity.CountUnit countUnit : countUnits) {
                if (countUnit.getMaxEntityCount() > 0) {
                    Long count = countUnit.getCountQueryProducer().apply(session).build(session).setFlushMode(FlushModeType.COMMIT).uniqueResult();

                    if (count > countUnit.getMaxEntityCount()) {
                        throw new InvalidCommandException(countUnit.getErrorMessage());
                    }
                }
            }
        });

        super.preFlush(entities);
    }
}
