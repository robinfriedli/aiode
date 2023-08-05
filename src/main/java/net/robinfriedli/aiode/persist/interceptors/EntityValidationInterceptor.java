package net.robinfriedli.aiode.persist.interceptors;

import java.io.Serializable;
import java.util.Set;

import org.slf4j.Logger;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import net.robinfriedli.aiode.exceptions.InvalidPropertyValueException;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

/**
 * Interceptor that validates constraints defined by JPA validation annotations (e.g. {@link Size}) and throws an
 * {@link InvalidPropertyValueException} if one is violated.
 */
public class EntityValidationInterceptor extends ChainableInterceptor {

    public EntityValidationInterceptor(Interceptor next, Logger logger) {
        super(next, logger);
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        validateEntity(entity);

        return next().onSave(entity, id, state, propertyNames, types);
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        validateEntity(entity);

        return next().onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    private void validateEntity(Object entity) {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<Object>> constraintViolations = validator.validate(entity);
            if (!constraintViolations.isEmpty()) {
                int i = 0;
                StringBuilder errorMessage = new StringBuilder();
                for (ConstraintViolation<Object> constraintViolation : constraintViolations) {
                    String message = constraintViolation.getMessage();
                    int messageLength = message.length();
                    if (messageLength >= 1000) {
                        throw new IllegalStateException("Message of constraint " + constraintViolation.getPropertyPath().toString() + " is too long.");
                    }
                    if (messageLength + errorMessage.length() < 1000) {
                        errorMessage.append(message);
                        if (i < constraintViolations.size() - 1) {
                            errorMessage.append(System.lineSeparator());
                        }
                    } else {
                        break;
                    }
                    ++i;
                }

                throw new InvalidPropertyValueException(errorMessage.toString());
            }
        }
    }

}
