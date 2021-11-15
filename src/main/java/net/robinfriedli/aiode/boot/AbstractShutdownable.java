package net.robinfriedli.aiode.boot;

public abstract class AbstractShutdownable implements Shutdownable {

    protected AbstractShutdownable() {
        register();
    }
}
