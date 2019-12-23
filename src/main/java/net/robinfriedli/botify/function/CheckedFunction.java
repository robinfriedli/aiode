package net.robinfriedli.botify.function;

import java.util.function.Function;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;

/**
 * Function that handles checked exceptions by wrapping them into {@link CommandRuntimeException}
 *
 * @param <P> the type of the parameter
 * @param <R> the return type
 */
@FunctionalInterface
public interface CheckedFunction<P, R> extends Function<P, R> {

    @Override
    default R apply(P p) {
        try {
            return doApply(p);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        }
    }

    R doApply(P p) throws Exception;

}
