package net.robinfriedli.botify.function;

public abstract class ChainableRunnable implements CheckedRunnable {

    public Runnable andThen(Runnable next) {
        return () -> {
            run();
            next.run();
        };
    }

}
