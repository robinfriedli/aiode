package net.robinfriedli.botify.concurrent;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.exceptions.handlers.TrackLoadingExceptionHandler;

/**
 * Executes loading track information asynchronously, e.g. when populating a YouTube playlist or redirecting
 * Spotify tracks (see {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}). There is one GuildTrackLoadingExecutor
 * per guild to manage how many track loading threads a guild can run concurrently.
 */
public class GuildTrackLoadingExecutor {

    private final GuildContext guildContext;
    private final ThreadExecutionQueue threadExecutionQueue;
    private final Logger logger;
    // register Track loading Threads here so that they can be interrupted when a different playlist is being played
    private Thread trackLoadingThread;

    public GuildTrackLoadingExecutor(GuildContext guildContext) {
        this.guildContext = guildContext;
        threadExecutionQueue = new ThreadExecutionQueue(3);
        logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Perform the given action either as {@link QueuedThread} in the {@link ThreadExecutionQueue} or as single
     * interruptible thread, to learn more about interruptible track loading see
     * {@link PlayableFactory#createPlayables(boolean, Collection, boolean)}
     *
     * @param r            the action to run
     * @param singleThread if true creates a single thread that might get interrupted and replaced by the next action
     */
    public void load(CheckedRunnable r, boolean singleThread) {
        Thread thread = singleThread ? new Thread(r) : new QueuedThread(threadExecutionQueue, r);
        String kind = singleThread ? "interruptible" : "parallel";
        String name = "botify " + kind + " track loading thread";
        MessageChannel channel;
        CommandContext commandContext = null;
        if (CommandContext.Current.isSet()) {
            commandContext = CommandContext.Current.require();
            channel = commandContext.getChannel();
            thread.setName(name + " " + commandContext.toString());
        } else {
            channel = getGuildContext().getPlayback().getCommunicationChannel();
            thread.setName(name);
        }
        thread.setUncaughtExceptionHandler(new TrackLoadingExceptionHandler(logger, channel, commandContext));
        if (singleThread) {
            registerTrackLoading(thread);
            thread.start();
        } else {
            threadExecutionQueue.add((QueuedThread) thread);
        }
    }

    private void registerTrackLoading(Thread thread) {
        if (this.trackLoadingThread != null) {
            interruptTrackLoading();
        }
        this.trackLoadingThread = thread;
    }

    public void interruptTrackLoading() {
        if (trackLoadingThread != null && trackLoadingThread.isAlive()) {
            trackLoadingThread.interrupt();
        }
    }

    public GuildContext getGuildContext() {
        return guildContext;
    }

    /**
     * Interrupts all commands that are currently loading tracks for this guild and cancel queued threads
     */
    public void interruptAll() {
        interruptTrackLoading();
        threadExecutionQueue.abortAll();
    }

    /**
     * @return true if there is no thread currently loading any tracks
     */
    public boolean isIdle() {
        return (trackLoadingThread == null || !trackLoadingThread.isAlive()) && threadExecutionQueue.isIdle();
    }

}
