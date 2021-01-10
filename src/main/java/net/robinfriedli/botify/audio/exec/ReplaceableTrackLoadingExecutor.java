package net.robinfriedli.botify.audio.exec;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.handler.TrackLoadingExceptionHandlerExecutor;

/**
 * TrackLoadingExecutor implementation that holds one single thread to execute the task with asynchronously. If another
 * task is executed, the previous thread will be sent an interrupt signal. This is used for commands where the data can
 * be fetched in the background as it is not used by the command itself and where a new command eliminates the need to
 * load the data from the old command, such as the play command. Each guild context has their own instance of this class.
 */
public class ReplaceableTrackLoadingExecutor implements TrackLoadingExecutor {

    private final AtomicInteger threadNumber;
    private final AtomicReference<Future<?>> currentTask;
    private final GuildContext guildContext;

    public ReplaceableTrackLoadingExecutor(GuildContext guildContext) {
        threadNumber = new AtomicInteger(0);
        currentTask = new AtomicReference<>();
        this.guildContext = guildContext;
    }

    @Override
    public synchronized void execute(Runnable trackLoadingRunnable) {
        MessageChannel channel;
        ExecutionContext executionContext = null;
        if (ExecutionContext.Current.isSet()) {
            executionContext = ExecutionContext.Current.require();
            channel = executionContext.getChannel();
        } else {
            channel = guildContext.getPlayback().getCommunicationChannel();
        }

        Future<?> runningTask = currentTask.get();
        if (runningTask != null) {
            runningTask.cancel(true);
        }

        int expectedThreadNumber = threadNumber.incrementAndGet();
        ExecutionContext finalExecutionContext = executionContext != null ? executionContext.fork() : null;
        Future<?> future = PooledTrackLoadingExecutor.GLOBAL_POOL.submit(() -> {
            try {
                if (finalExecutionContext != null) {
                    ExecutionContext.Current.set(finalExecutionContext);
                }

                if (channel != null) {
                    ThreadContext.Current.install(channel);
                }


                trackLoadingRunnable.run();
            } catch (Exception e) {
                try {
                    new TrackLoadingExceptionHandlerExecutor(finalExecutionContext, channel).handleException(e);
                } catch (Throwable propagate) {
                    CommandRuntimeException.throwRuntimeException(propagate);
                }
            } finally {
                ThreadContext.Current.clear();
                if (expectedThreadNumber == threadNumber.get()) {
                    currentTask.set(null);
                }
            }
        });
        currentTask.set(future);
    }

    public void abort() {
        Future<?> future = currentTask.get();
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    public boolean isIdle() {
        Future<?> future = currentTask.get();
        return future == null || future.isDone();
    }

}
