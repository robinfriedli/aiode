package net.robinfriedli.botify.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.exceptions.TrackLoadingExceptionHandler;

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

    public void load(Runnable r, boolean singleThread) {
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
}
