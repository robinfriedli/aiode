package net.robinfriedli.botify.discord;

import java.time.Duration;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.util.ISnowflakeMap;

/**
 * Manages all command {@link ThreadExecutionQueue}s for all guilds.
 */
public class CommandExecutionQueueManager {

    private static final int EXECUTION_QUEUE_SIZE = 3;
    private static final int RATE_LIMIT_FOR_PERIOD = 4;
    private static final Duration RATE_LIMIT_PERIOD = Duration.ofSeconds(5);
    private static final Duration RATE_LIMIT_VIOLATION_TIMEOUT = Duration.ofSeconds(15);

    private final ISnowflakeMap<ThreadExecutionQueue> guildExecutionQueues;

    public CommandExecutionQueueManager() {
        this.guildExecutionQueues = new ISnowflakeMap<>();
    }

    public void addGuild(Guild guild) {
        guildExecutionQueues.put(
            guild,
            new ThreadExecutionQueue(
                EXECUTION_QUEUE_SIZE,
                false,
                guild.getId(),
                RATE_LIMIT_FOR_PERIOD,
                RATE_LIMIT_PERIOD,
                RATE_LIMIT_VIOLATION_TIMEOUT
            )
        );
    }

    public void removeGuild(Guild guild) {
        guildExecutionQueues.remove(guild);
    }

    public ThreadExecutionQueue getForGuild(Guild guild) {
        return guildExecutionQueues.computeIfAbsent(guild, g -> new ThreadExecutionQueue(
            EXECUTION_QUEUE_SIZE,
            false,
            g.getId(),
            RATE_LIMIT_FOR_PERIOD,
            RATE_LIMIT_PERIOD,
            RATE_LIMIT_VIOLATION_TIMEOUT
        ));
    }

    public ISnowflakeMap<ThreadExecutionQueue> getGuildExecutionQueues() {
        return guildExecutionQueues;
    }

    public void joinAll() throws InterruptedException {
        for (ThreadExecutionQueue queue : guildExecutionQueues.values()) {
            queue.join();
        }
    }

    public void closeAll() {
        guildExecutionQueues.values().forEach(ThreadExecutionQueue::close);
    }

}
