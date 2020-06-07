package net.robinfriedli.botify.function;

import java.util.function.BiFunction;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;

/**
 * BiFunction that handles checked exceptions by wrapping them into {@link CommandRuntimeException}
 *
 * @param <P1> the type of the first parameter
 * @param <P2> the type of the second parameter
 * @param <R>  the return type
 */
@FunctionalInterface
public interface CheckedBiFunction<P1, P2, R> extends BiFunction<P1, P2, R> {

    @Override
    default R apply(P1 p1, P2 p2) {
        try {
            return doApply(p1, p2);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        }
    }

    R doApply(P1 p1, P2 p2) throws Exception;

}
