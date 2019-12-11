package net.robinfriedli.botify.audio.exec;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.exceptions.handlers.TrackLoadingExceptionHandler;

/**
 * TrackLoadingExecutor implementation that holds one single thread to execute the task with asynchronously. If another
 * task is executed, the previous thread will be sent an interrupt signal. This is used for commands where the data can
 * be fetched in the background as it is not used by the command itself and where a new command eliminates the need to
 * load the data from the old command, such as the play command. Each guild context has their own instance of this class.
 */
public class ReplaceableTrackLoadingExecutor implements TrackLoadingExecutor {

    private final AtomicInteger threadNumber;
    private final AtomicReference<Thread> currentThreadReference;
    private final GuildContext guildContext;

    public ReplaceableTrackLoadingExecutor(GuildContext guildContext) {
        threadNumber = new AtomicInteger(1);
        currentThreadReference = new AtomicReference<>();
        this.guildContext = guildContext;
    }

    @Override
    public synchronized void execute(Runnable trackLoadingRunnable) {
        Thread thread = new ReplaceableThread(trackLoadingRunnable);
        thread.setName("replaceable-track-loading-guild-" + guildContext.getGuild().getId() + "-thread-" + threadNumber.getAndIncrement());
        MessageChannel channel;
        CommandContext commandContext = null;
        if (CommandContext.Current.isSet()) {
            commandContext = CommandContext.Current.require();
            channel = commandContext.getChannel();
        } else {
            channel = guildContext.getPlayback().getCommunicationChannel();
        }
        thread.setUncaughtExceptionHandler(new TrackLoadingExceptionHandler(LoggerFactory.getLogger(getClass()), channel, commandContext));
        registerTrackLoading(thread);
        thread.start();
    }

    private void registerTrackLoading(Thread thread) {
        Thread currentThread = currentThreadReference.get();
        if (currentThread != null && currentThread.isAlive()) {
            currentThread.interrupt();
        }
        currentThreadReference.set(thread);
    }

    public void abort() {
        Thread thread = currentThreadReference.get();
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    public boolean isIdle() {
        Thread thread = currentThreadReference.get();
        return thread == null || !thread.isAlive();
    }

    private final class ReplaceableThread extends Thread {

        private ReplaceableThread(Runnable runnable) {
            super(runnable);
        }

        @Override
        public void run() {
            try {
                super.run();
            } finally {
                currentThreadReference.compareAndExchange(this, null);
            }
        }
    }

}
