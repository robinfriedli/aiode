package net.robinfriedli.botify.function;

import java.util.function.Consumer;
import java.util.function.Function;

import net.robinfriedli.exec.Mode;

/**
 * Implementations may manage calling a function in a certain way, e.g. setting up a transaction.
 *
 * @param <P> the type of the function parameter
 */
public interface FunctionInvoker<P> {

    <V> V invokeFunction(Function<P, V> function);

    default void invokeConsumer(Consumer<P> consumer) {
        invokeFunction(p -> {
            consumer.accept(p);
            return null;
        });
    }

    <V> V invokeFunction(Mode mode, Function<P, V> function);

    default void invokeConsumer(Mode mode, Consumer<P> consumer) {
        invokeFunction(mode, p -> {
            consumer.accept(p);
            return null;
        });
    }

}
