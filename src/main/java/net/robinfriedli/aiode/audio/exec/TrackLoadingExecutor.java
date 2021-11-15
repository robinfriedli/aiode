package net.robinfriedli.aiode.audio.exec;

/**
 * Interface whose implementations will specify how tracks, async pooled / replaceable or blocking
 */
public interface TrackLoadingExecutor {

    void execute(Runnable trackLoadingRunnable);

}
