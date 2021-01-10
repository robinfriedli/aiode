package net.robinfriedli.botify.concurrent;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.LoggerFactory;

import com.google.api.client.util.Lists;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.exceptions.handler.handlers.CommandUncaughtExceptionHandler;
import net.robinfriedli.botify.util.SnowflakeMap;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Manages all command {@link ThreadExecutionQueue}s for all guilds.
 */
@Component
public class CommandExecutionQueueManager {

    private static final ThreadPoolExecutor GLOBAL_POOL = new ThreadPoolExecutor(3, Integer.MAX_VALUE,
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new ThreadFactory() {
            private final AtomicLong threadId = new AtomicLong(1);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("command-execution-queue-idle-thread-" + threadId.getAndIncrement());
                thread.setUncaughtExceptionHandler(new CommandUncaughtExceptionHandler(LoggerFactory.getLogger(Command.class)));
                return thread;
            }
        });

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(GLOBAL_POOL));
    }

    private final SnowflakeMap<ThreadExecutionQueue> guildExecutionQueues;

    public CommandExecutionQueueManager() {
        this.guildExecutionQueues = new SnowflakeMap<>();
    }

    public void addGuild(Guild guild) {
        guildExecutionQueues.put(guild, new ThreadExecutionQueue("command-execution-queue-guild-" + guild.getId(), 3, GLOBAL_POOL));
    }

    public void removeGuild(Guild guild) {
        guildExecutionQueues.remove(guild);
    }

    public ThreadExecutionQueue getForGuild(Guild guild) {
        ThreadExecutionQueue threadExecutionQueue = guildExecutionQueues.get(guild);

        if (threadExecutionQueue == null) {
            ThreadExecutionQueue newQueue = new ThreadExecutionQueue("command-execution-queue-guild-" + guild.getId(), 3, GLOBAL_POOL);
            guildExecutionQueues.put(guild, newQueue);
            return newQueue;
        }

        return threadExecutionQueue;
    }

    public SnowflakeMap<ThreadExecutionQueue> getGuildExecutionQueues() {
        return guildExecutionQueues;
    }

    public void joinAll(long millis) throws InterruptedException {
        long millisLeft = millis;
        for (ThreadExecutionQueue queue : Lists.newArrayList(guildExecutionQueues.values())) {
            if (millisLeft < 0) {
                return;
            }

            long currentMillis = System.currentTimeMillis();
            queue.join(millisLeft);
            millisLeft -= (System.currentTimeMillis() - currentMillis);
        }
    }

    public void closeAll() {
        guildExecutionQueues.values().forEach(ThreadExecutionQueue::close);
    }

    /**
     * Cancels all tasks that are enqueued for all queues.
     */
    public void cancelEnqueued() {
        for (ThreadExecutionQueue queue : Lists.newArrayList(guildExecutionQueues.values())) {
            queue.cancelEnqueued();
        }
    }

}
