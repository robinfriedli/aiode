package net.robinfriedli.botify.listeners;

import java.util.Optional;

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
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.exceptions.handlers.WidgetExceptionHandler;
import net.robinfriedli.botify.util.StaticSessionProvider;

/**
 * Listener responsible for handling reaction events and widget execution
 */
public class WidgetListener extends ListenerAdapter {

    private final CommandManager commandManager;
    private final Logger logger;
    private final MessageService messageService;

    public WidgetListener(CommandManager commandManager, MessageService messageService) {
        this.commandManager = commandManager;
        this.messageService = messageService;
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getUser().isBot()) {

            String messageId = event.getMessageId();

            Optional<AbstractWidget> activeWidget = commandManager.getActiveWidget(messageId);
            activeWidget.ifPresent(abstractWidget -> handleWidgetExecution(event, messageId, abstractWidget));
        }
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        commandManager.getActiveWidget(event.getMessageId()).ifPresent(widget -> {
            widget.setMessageDeleted(true);
            widget.destroy();
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
