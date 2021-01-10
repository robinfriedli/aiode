package net.robinfriedli.botify.concurrent;

/**
 * Interface for contexts that may be installed on the {@link ThreadContext} that should close resources when the ThreadContext
 * is cleared.
 */
public interface CloseableThreadContext {

    void close();

}
