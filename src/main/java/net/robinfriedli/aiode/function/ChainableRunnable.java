package net.robinfriedli.aiode.function;

public interface ChainableRunnable extends CheckedRunnable {

    default ChainableRunnable andThen(Runnable next) {
        return () -> {
            run();
            next.run();
        };
    }

}
