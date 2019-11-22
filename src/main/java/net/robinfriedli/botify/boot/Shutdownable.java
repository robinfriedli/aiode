package net.robinfriedli.botify.boot;

import net.robinfriedli.botify.Botify;

public interface Shutdownable {

    default void register() {
        Botify.SHUTDOWNABLES.add(this);
    }

    void shutdown(int delayMs);
}
