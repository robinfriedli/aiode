package net.robinfriedli.aiode.audio.exec;

/**
 * Interface whose implementations will specify how tracks are loaded, async pooled / replaceable or blocking
 */
public interface TrackLoadingExecutor {

    void execute(Runnable trackLoadingRunnable);

}
