package net.robinfriedli.aiode.function;

import net.robinfriedli.aiode.exceptions.CommandRuntimeException;

/**
 * Runnable that handles checked exceptions by wrapping them into {@link CommandRuntimeException}
 */
@FunctionalInterface
public interface CheckedRunnable extends Runnable {

    @Override
    default void run() {
        try {
            doRun();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        }
    }

    void doRun() throws Exception;

}
