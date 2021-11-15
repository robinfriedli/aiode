package net.robinfriedli.aiode.audio.exec;

import net.robinfriedli.aiode.command.commands.playlistmanagement.AddCommand;

/**
 * Simple TrackLoadingExecutor that performs the task blocking in the current thread. This is used for commands that
 * require all data to be fetched to continue, such as the {@link AddCommand} and its subclasses.
 */
public class BlockingTrackLoadingExecutor implements TrackLoadingExecutor {

    @Override
    public void execute(Runnable trackLoadingRunnable) {
        trackLoadingRunnable.run();
    }
}
