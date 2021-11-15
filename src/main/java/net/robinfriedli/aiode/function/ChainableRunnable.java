package net.robinfriedli.aiode.function;

public abstract class ChainableRunnable implements CheckedRunnable {

    public Runnable andThen(Runnable next) {
        return () -> {
            run();
            next.run();
        };
    }

}
