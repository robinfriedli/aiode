package net.robinfriedli.botify.audio.exec;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.concurrent.QueuedTask;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.handler.TrackLoadingExceptionHandlerExecutor;
import net.robinfriedli.botify.exceptions.handler.handlers.TrackLoadingUncaughtExceptionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * TrackLoadingExecutor implementation that performs the action async and, unlike the {@link ReplaceableTrackLoadingExecutor},
 * allows several actions to run concurrently, buffered through a {@link ThreadExecutionQueue} that allows 3 concurrent
 * threads. This is used for commands where the data can be fetched in the background as it is not used by the command
 * itself and where new commands should not cancel the data to be fetched by an old command, such as the queue command.
 * Each guild context has their own instance of this class.
 */
public class PooledTrackLoadingExecutor implements TrackLoadingExecutor {

    static final ThreadPoolExecutor GLOBAL_POOL = new ThreadPoolExecutor(3, Integer.MAX_VALUE,
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new ThreadFactory() {
            private final AtomicLong threadId = new AtomicLong(1);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("pooled-track-loading-thread-" + threadId.getAndIncrement());
                thread.setUncaughtExceptionHandler(new TrackLoadingUncaughtExceptionHandler(LoggerFactory.getLogger(PooledTrackLoadingExecutor.class)));
                return thread;
            }
        });

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(GLOBAL_POOL));
    }

    private final ThreadExecutionQueue queue;
    private final GuildContext guildContext;

    public PooledTrackLoadingExecutor(String guildId, GuildContext guildContext) {
        this.queue = new ThreadExecutionQueue("pooled-track-loading-guild-" + guildId, 3, GLOBAL_POOL);
        this.guildContext = guildContext;
    }

    @Override
    public void execute(Runnable trackLoadingRunnable) {
        MessageChannel channel;
        ExecutionContext executionContext = null;
        if (ExecutionContext.Current.isSet()) {
            executionContext = ExecutionContext.Current.require();
            channel = executionContext.getChannel();
        } else {
            channel = guildContext.getPlayback().getCommunicationChannel();
        }
        ExecutionContext finalExecutionContext = executionContext != null ? executionContext.fork() : null;
        QueuedTask thread = new QueuedTask(queue, () -> {
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
            }
        });
        queue.add(thread);
    }

    public void abortAll() {
        queue.abortAll();
    }

    public boolean isIdle() {
        return queue.isIdle();
    }

}
