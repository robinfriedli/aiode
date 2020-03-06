package net.robinfriedli.botify.discord.listeners;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.commands.customisation.RenameCommand;
import net.robinfriedli.botify.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.botify.concurrent.EventHandlerPool;
import net.robinfriedli.botify.concurrent.LoggingThreadFactory;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.botify.entities.GrantedRole;
import org.discordbots.api.client.DiscordBotListAPI;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Listener responsible for handling the bot joining or leaving a guild or relevant changes to the guild configuration
 */
@Component
public class GuildManagementListener extends ListenerAdapter {

    private static final ExecutorService STATS_UPDATE_POOL = new ThreadPoolExecutor(0, 1,
        10L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        new LoggingThreadFactory("topgg-stats-update-pool"));

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(STATS_UPDATE_POOL));
    }

    private final CommandExecutionQueueManager executionQueueManager;
    @Nullable
    private final DiscordBotListAPI discordBotListAPI;
    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final Logger logger;
    private final MessageService messageService;
    private final ShardManager shardManager;

    public GuildManagementListener(CommandExecutionQueueManager executionQueueManager,
                                   @Nullable DiscordBotListAPI discordBotListAPI,
                                   GuildManager guildManager,
                                   HibernateComponent hibernateComponent,
                                   MessageService messageService,
                                   ShardManager shardManager) {
        this.executionQueueManager = executionQueueManager;
        this.discordBotListAPI = discordBotListAPI;
        this.messageService = messageService;
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        logger = LoggerFactory.getLogger(getClass());
        this.shardManager = shardManager;
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        if (discordBotListAPI != null) {
            STATS_UPDATE_POOL.execute(() -> {
                Guild guild = event.getGuild();
                guildManager.addGuild(guild);
                executionQueueManager.addGuild(guild);

                for (JDA shard : shardManager.getShards()) {
                    JDA.ShardInfo shardInfo = shard.getShardInfo();
                    discordBotListAPI.setStats(shardInfo.getShardId(), shardInfo.getShardTotal(), shard.getGuilds().size());
                }
            });
        }
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        if (discordBotListAPI != null) {
            STATS_UPDATE_POOL.execute(() -> {
                Guild guild = event.getGuild();
                guildManager.removeGuild(guild);
                executionQueueManager.removeGuild(guild);

                for (JDA shard : shardManager.getShards()) {
                    JDA.ShardInfo shardInfo = shard.getShardInfo();
                    discordBotListAPI.setStats(shardInfo.getShardId(), shardInfo.getShardTotal(), shard.getGuilds().size());
                }
            });
        }
    }

    @Override
    public void onRoleDelete(@Nonnull RoleDeleteEvent event) {
        EventHandlerPool.execute(() -> {
            Role role = event.getRole();
            String roleId = role.getId();
            hibernateComponent.consumeSession(session -> {
                CriteriaBuilder cb = session.getCriteriaBuilder();
                CriteriaDelete<GrantedRole> deleteQuery = cb.createCriteriaDelete(GrantedRole.class);
                Root<GrantedRole> queryRoot = deleteQuery.from(GrantedRole.class);
                deleteQuery.where(cb.equal(queryRoot.get("id"), roleId));
                int rowCount = session.createQuery(deleteQuery).executeUpdate();

                if (rowCount > 0) {
                    Guild guild = event.getGuild();
                    GuildContext guildContext = guildManager.getContextForGuild(guild);
                    logger.info(String.format("Deleted %s GrantedRole entities upon deletion of role %s on guild %s", rowCount, roleId, guild));

                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("Deletion of role referenced by your permission configuration");
                    embedBuilder.setDescription(String.format("The deleted role '%s' was referenced by the permission configuration. Check the current permissions by using the permission command.", role.getName()));
                    embedBuilder.setColor(ColorSchemeProperty.getColor(guildContext.getSpecification(session)));
                    TextChannel textChannel = guildManager.getDefaultTextChannelForGuild(guild);
                    if (textChannel != null) {
                        messageService.sendTemporary(embedBuilder.build(), textChannel);
                    }
                }
            });
        });
    }

    @Override
    public void onGuildMemberUpdateNickname(@Nonnull GuildMemberUpdateNicknameEvent event) {
        Guild guild = event.getGuild();
        if (event.getMember().equals(guild.getSelfMember())) {
            EventHandlerPool.execute(() -> RenameCommand.RENAME_SYNC.execute(guild.getIdLong(), () -> {
                    GuildContext guildContext = guildManager.getContextForGuild(guild);
                    String botName = guildContext.getBotName();
                    String name = event.getNewNickname();
                    if (!Objects.equals(name, botName)) {
                        hibernateComponent.consumeSession(session -> {
                            guildContext.setBotName(name);

                            EmbedBuilder embedBuilder = new EmbedBuilder();
                            embedBuilder.setTitle("Renamed");
                            embedBuilder.setDescription(String.format("Botify has been renamed to '%s'. This new name can be used as command prefix.", name));
                            embedBuilder.setColor(ColorSchemeProperty.getColor(guildContext.getSpecification(session)));
                            TextChannel textChannel = guildManager.getDefaultTextChannelForGuild(guild);
                            if (textChannel != null) {
                                messageService.sendTemporary(embedBuilder.build(), textChannel);
                            }
                        });
                    }
                }
            ));
        }
    }
}
