package net.robinfriedli.aiode.cron.tasks;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.cron.AbstractCronTask;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.listeners.GuildManagementListener;
import net.robinfriedli.exec.Mode;
import org.quartz.JobExecutionContext;

/**
 * Task that periodically removes {@link GuildContext} instances belonging to guilds that are no longer associated with
 * this bot and whose exit was not picked up by the {@link GuildManagementListener}
 */
public class ClearAbandonedGuildContextsTask extends AbstractCronTask {

    @Override
    protected void run(JobExecutionContext jobExecutionContext) {
        Aiode aiode = Aiode.get();
        ShardManager shardManager = aiode.getShardManager();
        GuildManager guildManager = aiode.getGuildManager();
        CommandExecutionQueueManager executionQueueManager = aiode.getExecutionQueueManager();

        int removedGuilds = 0;
        for (GuildContext guildContext : guildManager.getGuildContexts()) {
            Guild guild = guildContext.retrieveGuild();
            if (guild == null || shardManager.getGuildById(guild.getIdLong()) == null) {
                guildManager.removeGuild(guild);
                executionQueueManager.removeGuild(guild);
                ++removedGuilds;
            }
        }

        if (removedGuilds > 0) {
            LoggerFactory.getLogger(getClass()).info("Destroyed context for " + removedGuilds + " missing guilds");
        }
    }

    @Override
    protected Mode getMode() {
        return Mode.create();
    }
}
