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

    <V> V invoke(Function<P, V> function);

    default void invoke(Consumer<P> consumer) {
        invoke(p -> {
            consumer.accept(p);
            return null;
        });
    }

    <V> V invoke(Mode mode, Function<P, V> function);

    default void invoke(Mode mode, Consumer<P> consumer) {
        invoke(mode, p -> {
            consumer.accept(p);
            return null;
        });
    }

}
