package net.robinfriedli.botify.audio.exec;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.concurrent.QueuedThread;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.exceptions.handlers.TrackLoadingExceptionHandler;

/**
 * TrackLoadingExecutor implementation that performs the action async and, unlike the {@link ReplaceableTrackLoadingExecutor},
 * allows several actions to run concurrently, buffered through a {@link ThreadExecutionQueue} that allows 3 concurrent
 * threads. This is used for commands where the data can be fetched in the background as it is not used by the command
 * itself and where new commands should not cancel the data to be fetched by an old command, such as the queue command.
 * Each guild context has their own instance of this class.
 */
public class PooledTrackLoadingExecutor implements TrackLoadingExecutor {

    private final ThreadExecutionQueue pool;
    private final GuildContext guildContext;

    public PooledTrackLoadingExecutor(ThreadExecutionQueue pool, GuildContext guildContext) {
        this.pool = pool;
        this.guildContext = guildContext;
    }

    @Override
    public void execute(Runnable trackLoadingRunnable) {
        QueuedThread thread = new QueuedThread(pool, trackLoadingRunnable);
        MessageChannel channel;
        CommandContext commandContext = null;
        if (CommandContext.Current.isSet()) {
            commandContext = CommandContext.Current.require();
            channel = commandContext.getChannel();
        } else {
            channel = guildContext.getPlayback().getCommunicationChannel();
        }
        thread.setUncaughtExceptionHandler(new TrackLoadingExceptionHandler(LoggerFactory.getLogger(getClass()), channel, commandContext));
        pool.add(thread);
    }

    public void abortAll() {
        pool.abortAll();
    }

    public boolean isIdle() {
        return pool.isIdle();
    }

}
