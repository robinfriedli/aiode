package net.robinfriedli.botify.listeners;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.GrantedRole;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import net.robinfriedli.botify.util.StaticSessionProvider;
import org.discordbots.api.client.DiscordBotListAPI;
import org.jetbrains.annotations.NotNull;

/**
 * Listener responsible for handling the bot joining or leaving a guild or relevant changes to the guild configuration
 */
public class GuildManagementListener extends ListenerAdapter {

    private final CommandExecutionQueueManager executionQueueManager;
    @Nullable
    private final DiscordBotListAPI discordBotListAPI;
    private final ExecutorService guildEventHandlerExecutorService;
    private final GuildManager guildManager;
    private final Logger logger;

    public GuildManagementListener(CommandExecutionQueueManager executionQueueManager,
                                   @Nullable DiscordBotListAPI discordBotListAPI,
                                   GuildManager guildManager) {
        this.executionQueueManager = executionQueueManager;
        this.discordBotListAPI = discordBotListAPI;
        guildEventHandlerExecutorService = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
        this.guildManager = guildManager;
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        guildEventHandlerExecutorService.execute(() -> {
            Guild guild = event.getGuild();
            guildManager.addGuild(guild);
            executionQueueManager.addGuild(guild);

            updateDiscordBotsApiStats(event);
        });
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        guildEventHandlerExecutorService.execute(() -> {
            Guild guild = event.getGuild();
            guildManager.removeGuild(guild);
            executionQueueManager.removeGuild(guild);

            updateDiscordBotsApiStats(event);
        });
    }

    @Override
    public void onRoleDelete(@Nonnull RoleDeleteEvent event) {
        guildEventHandlerExecutorService.execute(() -> {
            String roleId = event.getRole().getId();
            StaticSessionProvider.consumeSession(session -> {
                CriteriaBuilder cb = session.getCriteriaBuilder();
                CriteriaDelete<GrantedRole> deleteQuery = cb.createCriteriaDelete(GrantedRole.class);
                Root<GrantedRole> queryRoot = deleteQuery.from(GrantedRole.class);
                deleteQuery.where(cb.equal(queryRoot.get("id"), roleId));
                int rowCount = session.createQuery(deleteQuery).executeUpdate();

                if (rowCount > 0) {
                    logger.info(String.format("Deleted %s GrantedRole entities upon deletion of role %s on guild %s", rowCount, roleId, event.getGuild()));
                }
            });
        });
    }

    private void updateDiscordBotsApiStats(Event event) {
        if (discordBotListAPI != null) {
            try {
                JDA jda = event.getJDA();
                JDA.ShardInfo shardInfo = jda.getShardInfo();
                discordBotListAPI.setStats(shardInfo.getShardId(), shardInfo.getShardTotal(), (int) jda.getGuildCache().size());
            } catch (Exception e) {
                logger.error("Exception setting discordBotListAPI stats", e);
            }
        }
    }

}
