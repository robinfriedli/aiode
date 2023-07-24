package net.robinfriedli.aiode.discord.listeners;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.concurrent.EventHandlerPool;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.exceptions.UserException;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Listener responsible for handling reaction events and widget execution
 */
@Component
public class WidgetListener extends ListenerAdapter {

    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final Logger logger;
    private final MessageService messageService;

    public WidgetListener(GuildManager guildManager, HibernateComponent hibernateComponent, MessageService messageService) {
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.messageService = messageService;
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        User user = event.getUser();
        if (!user.isBot()) {
            EventHandlerPool.execute(() -> {
                long messageId = event.getMessageIdLong();
                WidgetRegistry widgetRegistry = guildManager.getContextForGuild(event.getGuild()).getWidgetRegistry();

                Optional<AbstractWidget> activeWidget = widgetRegistry.getActiveWidget(messageId);
                activeWidget.ifPresent(abstractWidget -> handleWidgetExecution(event, abstractWidget));
            });
        }
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        EventHandlerPool.execute(() -> {
            WidgetRegistry widgetRegistry = guildManager.getContextForGuild(event.getGuild()).getWidgetRegistry();
            widgetRegistry.getActiveWidget(event.getMessageIdLong()).ifPresent(widget -> {
                widget.setMessageDeleted(true);
                widget.destroy();
            });
        });
    }

    private void handleWidgetExecution(ButtonInteractionEvent event, AbstractWidget activeWidget) {
        MessageChannelUnion channel = event.getChannel();
        Guild guild = event.getGuild();
        Aiode aiode = Aiode.get();
        SpotifyApi.Builder spotifyApiBuilder = aiode.getSpotifyApiBuilder();
        GuildContext guildContext = aiode.getGuildManager().getContextForGuild(guild);

        try {
            Message message = activeWidget.getMessage().retrieve();

            if (message == null) {
                throw new IllegalStateException("Message of widget could not be retrieved.");
            }

            EmojiUnion emoji = event.getButton().getEmoji();
            CommandContext commandContext = new CommandContext(
                event,
                guildContext,
                message.getContentDisplay(),
                hibernateComponent.getSessionFactory(),
                spotifyApiBuilder,
                emoji != null ? emoji.getAsReactionCode() : event.getButton().getLabel()
            );

            activeWidget.handleButtonInteraction(event, commandContext);
        } catch (UserException e) {
            messageService.sendError(e.getMessage(), channel);
        } catch (InsufficientPermissionException e) {
            Permission permission = e.getPermission();
            messageService.send("Bot is missing permission: " + permission.getName(), channel);
            logger.warn(String.format("Missing permission %s on guild %s", permission, guild));
        } catch (Exception e) {
            logger.error("Exception while handling WidgetAction execution.", e);
        }
    }

}
