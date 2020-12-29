package net.robinfriedli.botify.command.widget.widgets;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.widget.AbstractDecoratingWidget;
import net.robinfriedli.botify.command.widget.WidgetRegistry;
import net.robinfriedli.botify.concurrent.CompletableFutures;
import net.robinfriedli.botify.discord.MessageService;

public class QueueWidget extends AbstractDecoratingWidget {

    private final AudioManager audioManager;
    private final AudioPlayback audioPlayback;

    public QueueWidget(WidgetRegistry widgetRegistry, Guild guild, Message message, AudioManager audioManager, AudioPlayback audioPlayback) {
        super(widgetRegistry, guild, message);
        this.audioManager = audioManager;
        this.audioPlayback = audioPlayback;
    }

    @Override
    public void reset() {
        MessageService messageService = Botify.get().getMessageService();
        Message message = getMessage().retrieve();

        if (message != null) {
            awaitMessageDeletion();
            try {
                message.delete().queue(_v -> setMessageDeleted(true), e -> handleDeletionError(e, message));
            } catch (InsufficientPermissionException e) {
                setMessageDeleted(false);
                messageService.sendError("Bot is missing permission: " + e.getPermission().getName(), message.getChannel());
            } catch (Exception e) {
                handleDeletionError(e, message);
            }
        }

        Guild guild = getGuild().get();
        MessageChannel channel = getChannel().get();
        EmbedBuilder embedBuilder = audioPlayback.getAudioQueue().buildMessageEmbed(audioPlayback, guild);
        CompletableFuture<Message> futureMessage = messageService.send(embedBuilder, channel);
        WidgetRegistry widgetRegistry = getWidgetRegistry();
        CompletableFutures.thenAccept(
            futureMessage,
            completedMessage -> new QueueWidget(widgetRegistry, guild, completedMessage, audioManager, audioPlayback).initialise()
        );
    }

    private void handleDeletionError(Throwable e, Message message) {
        setMessageDeleted(false);
        OffsetDateTime timeCreated = message.getTimeCreated();
        ZonedDateTime zonedDateTime = timeCreated.atZoneSameInstant(ZoneId.systemDefault());
        LoggerFactory.getLogger(getClass()).warn(String.format("Cannot delete queue widget message from %s for channel %s on guild %s",
            zonedDateTime, message.getChannel(), message.getGuild()), e);
    }

}
