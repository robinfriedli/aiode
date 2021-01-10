package net.robinfriedli.botify.discord.listeners;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widget.AbstractWidget;
import net.robinfriedli.botify.command.widget.WidgetRegistry;
import net.robinfriedli.botify.concurrent.EventHandlerPool;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.UserException;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

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
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getUser().isBot()) {
            EventHandlerPool.execute(() -> {
                long messageId = event.getMessageIdLong();
                WidgetRegistry widgetRegistry = guildManager.getContextForGuild(event.getGuild()).getWidgetRegistry();

                Optional<AbstractWidget> activeWidget = widgetRegistry.getActiveWidget(messageId);
                activeWidget.ifPresent(abstractWidget -> handleWidgetExecution(event, abstractWidget));
            });
        }
    }

    @Override
    public void onGuildMessageDelete(@NotNull GuildMessageDeleteEvent event) {
        EventHandlerPool.execute(() -> {
            WidgetRegistry widgetRegistry = guildManager.getContextForGuild(event.getGuild()).getWidgetRegistry();
            widgetRegistry.getActiveWidget(event.getMessageIdLong()).ifPresent(widget -> {
                widget.setMessageDeleted(true);
                widget.destroy();
            });
        });
    }

    private void handleWidgetExecution(GuildMessageReactionAddEvent event, AbstractWidget activeWidget) {
        TextChannel channel = event.getChannel();
        Guild guild = event.getGuild();
        Botify botify = Botify.get();
        SpotifyApi.Builder spotifyApiBuilder = botify.getSpotifyApiBuilder();
        GuildContext guildContext = botify.getGuildManager().getContextForGuild(guild);
        String emojiUnicode = event.getReaction().getReactionEmote().getName();

        try {
            Message message = activeWidget.getMessage().retrieve();

            if (message == null) {
                throw new IllegalStateException("Message of widget could not be retrieved.");
            }

            CommandContext commandContext = new CommandContext(
                event,
                guildContext,
                message,
                hibernateComponent.getSessionFactory(),
                spotifyApiBuilder,
                emojiUnicode
            );

            activeWidget.handleReaction(event, commandContext);
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
