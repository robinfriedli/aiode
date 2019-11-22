package net.robinfriedli.botify.boot;

public abstract class AbstractShutdownable implements Shutdownable {

    protected AbstractShutdownable() {
        register();
    }
}
