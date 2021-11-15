package net.robinfriedli.aiode.boot;

import net.robinfriedli.aiode.Aiode;

public interface Shutdownable {

    default void register() {
        Aiode.SHUTDOWNABLES.add(this);
    }

    void shutdown(int delayMs);
}
