package net.robinfriedli.botify.cron.tasks;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.cron.AbstractCronTask;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.listeners.GuildManagementListener;
import net.robinfriedli.jxp.exec.Invoker;
import org.quartz.JobExecutionContext;

/**
 * Task that periodically removes {@link GuildContext} instances belonging to guilds that are no longer associated with
 * this bot and whose exit was not picked up by the {@link GuildManagementListener}
 */
public class ClearAbandonedGuildContextsTask extends AbstractCronTask {

    @Override
    protected void run(JobExecutionContext jobExecutionContext) {
        Botify botify = Botify.get();
        JDA jda = botify.getJda();
        GuildManager guildManager = botify.getGuildManager();
        CommandExecutionQueueManager executionQueueManager = botify.getExecutionQueueManager();

        int removedGuilds = 0;
        for (GuildContext guildContext : guildManager.getGuildContexts()) {
            Guild guild = guildContext.getGuild();
            if (jda.getGuildById(guild.getIdLong()) == null) {
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
    protected Invoker.Mode getMode() {
        return Invoker.Mode.create();
    }
}
