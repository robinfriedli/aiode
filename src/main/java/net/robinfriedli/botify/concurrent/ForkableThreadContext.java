package net.robinfriedli.botify.concurrent;

import javax.annotation.Nullable;

public interface ForkableThreadContext<T extends ForkableThreadContext<T>> {

    /**
     * Set the thread where this context was installed.
     */
    void setThread(Thread thread);

    /**
     * @return a fork of this context to be used in a fork task. If this returns null the context will not be installed
     * on the forked task.
     */
    @Nullable
    T fork();

}
