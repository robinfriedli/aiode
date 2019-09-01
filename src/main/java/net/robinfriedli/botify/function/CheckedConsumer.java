package net.robinfriedli.botify.function;

import java.util.function.Consumer;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;

/**
 * Consumer that handles checked exceptions by wrapping them into {@link CommandRuntimeException}
 *
 * @param <T> the type of parameter to execute an operation on
 */
public interface CheckedConsumer<T> extends Consumer<T> {

    @Override
    default void accept(T t) {
        try {
            doAccept(t);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        }
    }

    void doAccept(T t) throws Exception;

}
