package net.robinfriedli.botify.listeners;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import org.discordbots.api.client.DiscordBotListAPI;
import org.jetbrains.annotations.NotNull;

/**
 * Listener responsible for handling the bot joining or leaving a guild
 */
public class GuildJoinListener extends ListenerAdapter {

    private final CommandExecutionQueueManager executionQueueManager;
    @Nullable
    private final DiscordBotListAPI discordBotListAPI;
    private final ExecutorService guildJoinExecutorService;
    private final GuildManager guildManager;

    public GuildJoinListener(CommandExecutionQueueManager executionQueueManager,
                             @Nullable DiscordBotListAPI discordBotListAPI,
                             GuildManager guildManager) {
        this.executionQueueManager = executionQueueManager;
        this.discordBotListAPI = discordBotListAPI;
        guildJoinExecutorService = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
        this.guildManager = guildManager;
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        guildJoinExecutorService.execute(() -> {
            Guild guild = event.getGuild();
            guildManager.addGuild(guild);
            executionQueueManager.addGuild(guild);

            if (discordBotListAPI != null) {
                discordBotListAPI.setStats(event.getJDA().getGuilds().size());
            }
        });
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        guildJoinExecutorService.execute(() -> {
            Guild guild = event.getGuild();
            guildManager.removeGuild(guild);
            executionQueueManager.removeGuild(guild);

            if (discordBotListAPI != null) {
                discordBotListAPI.setStats(event.getJDA().getGuilds().size());
            }
        });
    }

}
