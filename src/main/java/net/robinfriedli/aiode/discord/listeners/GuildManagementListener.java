package net.robinfriedli.aiode.discord.listeners;

import java.time.OffsetDateTime;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Root;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.boot.configurations.TopGGComponent;
import net.robinfriedli.aiode.command.commands.customisation.RenameCommand;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.concurrent.EventHandlerPool;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.aiode.entities.GrantedRole;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.PrivateBotInstance;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Listener responsible for handling the bot joining or leaving a guild or relevant changes to the guild configuration
 */
@Component
public class GuildManagementListener extends ListenerAdapter {

    private final CommandExecutionQueueManager executionQueueManager;
    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final Logger logger;
    private final MessageService messageService;
    private final TopGGComponent topGGComponent;

    private final String privateInstanceIdentifier;

    public GuildManagementListener(CommandExecutionQueueManager executionQueueManager,
                                   GuildManager guildManager,
                                   HibernateComponent hibernateComponent,
                                   MessageService messageService,
                                   TopGGComponent topGGComponent,
                                   @Value("${aiode.preferences.private_instance_identifier}") String privateInstanceIdentifier) {
        this.executionQueueManager = executionQueueManager;
        this.topGGComponent = topGGComponent;
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        logger = LoggerFactory.getLogger(getClass());
        this.messageService = messageService;

        this.privateInstanceIdentifier = privateInstanceIdentifier;
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        EventHandlerPool.execute(() -> {
            Guild guild = event.getGuild();

            guildManager.addGuild(guild);
            executionQueueManager.addGuild(guild);

            if (!Strings.isNullOrEmpty(privateInstanceIdentifier)) {
                PrivateBotInstance assignedBotInstance = hibernateComponent.invokeWithSession(session -> guildManager.getContextForGuild(guild).getSpecification(session).getPrivateBotInstance());
                if (assignedBotInstance == null || !privateInstanceIdentifier.equals(assignedBotInstance.getIdentifier())) {
                    TextChannel textChannel = guildManager.getDefaultTextChannelForGuild(guild);
                    if (textChannel != null) {
                        messageService.sendError("This is a private bot instance that is not assigned to this guild / server. Use the invite command to assign a private bot to your server.", textChannel, false)
                            .whenComplete((r, e) -> {
                                if (e != null) {
                                    logger.warn("Failed to send unassigned private bot error message", e);
                                }
                                guild.leave().queue();
                            });
                    } else {
                        guild.leave().queue();
                    }
                    guildManager.removeGuild(guild);
                    executionQueueManager.removeGuild(guild);
                } else {
                    hibernateComponent.consumeSession(session -> guildManager.getContextForGuild(guild).getSpecification(session).setPrivateBotAssignmentLastHeartbeat(OffsetDateTime.now()));
                }
            }

            updateDiscordBotsApiStats(event);
        });
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        EventHandlerPool.execute(() -> {
            Guild guild = event.getGuild();

            if (!Strings.isNullOrEmpty(privateInstanceIdentifier)) {
                hibernateComponent.consumeSession(session -> {
                    GuildSpecification specification = guildManager.getContextForGuild(guild).getSpecification(session);
                    specification.setPrivateBotInstance(null);
                });
                logger.info("Unassigned private bot instance {} for guild {}", privateInstanceIdentifier, guild);
            }

            guildManager.removeGuild(guild);
            executionQueueManager.removeGuild(guild);

            updateDiscordBotsApiStats(event);
        });
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
                int rowCount = session.createMutationQuery(deleteQuery).executeUpdate();

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
            EventHandlerPool.execute(() -> RenameCommand.RENAME_SYNC.run(guild.getIdLong(), () -> {
                    GuildContext guildContext = guildManager.getContextForGuild(guild);
                    String botName = guildContext.getBotName();
                    String name = event.getNewNickname();
                    if (!Objects.equals(name, botName)) {
                        hibernateComponent.consumeSession(session -> {
                            guildContext.setBotName(name);

                            SpringPropertiesConfig springPropertiesConfig = Aiode.get().getSpringPropertiesConfig();
                            boolean messageContentEnabled = Objects.requireNonNullElse(springPropertiesConfig.getApplicationProperty(Boolean.class, "aiode.preferences.enable_message_content"), true);

                            if (messageContentEnabled) {
                                EmbedBuilder embedBuilder = new EmbedBuilder();
                                embedBuilder.setTitle("Renamed");
                                embedBuilder.setDescription(String.format("Aiode has been renamed to '%s'. This new name can be used as command prefix.", name));
                                embedBuilder.setColor(ColorSchemeProperty.getColor(guildContext.getSpecification(session)));
                                TextChannel textChannel = guildManager.getDefaultTextChannelForGuild(guild);
                                if (textChannel != null) {
                                    messageService.sendTemporary(embedBuilder.build(), textChannel);
                                }
                            }
                        });
                    }
                }
            ));
        }
    }

    private void updateDiscordBotsApiStats(Event event) {
        topGGComponent.updateStatsForShard(event.getJDA());
    }

}
