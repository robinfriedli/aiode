package net.robinfriedli.botify.discord.listeners;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.Shutdownable;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.concurrent.LoggingThreadFactory;
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
public class WidgetListener extends ListenerAdapter implements Shutdownable {

    private final GuildManager guildManager;
    private final ExecutorService executorService;
    private final HibernateComponent hibernateComponent;
    private final Logger logger;
    private final MessageService messageService;

    public WidgetListener(GuildManager guildManager, HibernateComponent hibernateComponent, MessageService messageService) {
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.messageService = messageService;
        executorService = Executors.newCachedThreadPool(new LoggingThreadFactory("widget-listener-pool"));
        logger = LoggerFactory.getLogger(getClass());
        register();
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getUser().isBot()) {
            executorService.execute(() -> {
                String messageId = event.getMessageId();
                WidgetManager widgetManager = guildManager.getContextForGuild(event.getGuild()).getWidgetManager();

                Optional<AbstractWidget> activeWidget = widgetManager.getActiveWidget(messageId);
                activeWidget.ifPresent(abstractWidget -> handleWidgetExecution(event, abstractWidget));
            });
        }
    }

    @Override
    public void onGuildMessageDelete(@NotNull GuildMessageDeleteEvent event) {
        executorService.execute(() -> {
            WidgetManager widgetManager = guildManager.getContextForGuild(event.getGuild()).getWidgetManager();
            widgetManager.getActiveWidget(event.getMessageId()).ifPresent(widget -> {
                widget.setMessageDeleted(true);
                widget.destroy();
            });
        });
    }

    private void handleWidgetExecution(GuildMessageReactionAddEvent event, AbstractWidget activeWidget) {
        TextChannel channel = event.getChannel();
        Guild guild = event.getGuild();
        Botify botify = Botify.get();
        SpotifyApi spotifyApi = botify.getSpotifyApiBuilder().build();
        GuildContext guildContext = botify.getGuildManager().getContextForGuild(guild);
        String emojiUnicode = event.getReaction().getReactionEmote().getName();
        CommandContext commandContext = new CommandContext(
            event,
            guildContext,
            activeWidget.getMessage(),
            hibernateComponent.getSessionFactory(),
            spotifyApi,
            emojiUnicode
        );

        try {
            activeWidget.handleReaction(event, commandContext);
        } catch (UserException e) {
            messageService.sendError(e.getMessage(), channel);
        } catch (Throwable e) {
            logger.error("Exception while preparing WidgetAction execution.", e);
        }
    }

    @Override
    public void shutdown(int delayMs) {
        executorService.shutdown();
    }
}
