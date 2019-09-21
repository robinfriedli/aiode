package net.robinfriedli.botify.listeners;

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
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import net.robinfriedli.botify.exceptions.handlers.WidgetExceptionHandler;
import net.robinfriedli.botify.util.StaticSessionProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Listener responsible for handling reaction events and widget execution
 */
public class WidgetListener extends ListenerAdapter {

    private final GuildManager guildManager;
    private final ExecutorService executorService;
    private final Logger logger;
    private final MessageService messageService;

    public WidgetListener(GuildManager guildManager, MessageService messageService) {
        this.guildManager = guildManager;
        this.messageService = messageService;
        executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getUser().isBot()) {
            executorService.execute(() -> {
                String messageId = event.getMessageId();
                WidgetManager widgetManager = guildManager.getContextForGuild(event.getGuild()).getWidgetManager();

                Optional<AbstractWidget> activeWidget = widgetManager.getActiveWidget(messageId);
                activeWidget.ifPresent(abstractWidget -> handleWidgetExecution(event, messageId, abstractWidget));
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

    private void handleWidgetExecution(GuildMessageReactionAddEvent event, String messageId, AbstractWidget activeWidget) {
        TextChannel channel = event.getChannel();
        Guild guild = event.getGuild();
        Botify botify = Botify.get();
        SpotifyApi spotifyApi = botify.getSpotifyApiBuilder().build();
        GuildContext guildContext = botify.getGuildManager().getContextForGuild(guild);
        CommandContext commandContext = new CommandContext("", activeWidget.getMessage(), StaticSessionProvider.getSessionFactory(), spotifyApi, guildContext);

        Thread widgetExecutionThread = new Thread(() -> {
            CommandContext.Current.set(commandContext);
            try {
                activeWidget.handleReaction(event);
            } catch (UserException e) {
                messageService.sendError(e.getMessage(), channel);
            } catch (Exception e) {
                throw new CommandRuntimeException(e);
            }
        });
        widgetExecutionThread.setName("Widget execution thread " + messageId);
        widgetExecutionThread.setUncaughtExceptionHandler(new WidgetExceptionHandler(channel, logger));
        widgetExecutionThread.start();
    }

}
