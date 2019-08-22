package net.robinfriedli.botify.concurrent;

import net.robinfriedli.botify.exceptions.CommandRuntimeException;

/**
 * Runnable that handles checked exceptions by wrapping them into {@link CommandRuntimeException}
 */
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
