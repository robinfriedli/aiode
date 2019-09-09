package net.robinfriedli.botify.command.widgets;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.widgets.actions.PlayPauseAction;
import net.robinfriedli.botify.command.widgets.actions.RewindAction;
import net.robinfriedli.botify.command.widgets.actions.SkipAction;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.util.EmojiConstants;

public class QueueWidget extends AbstractWidget {

    private final AudioManager audioManager;
    private final AudioPlayback audioPlayback;

    public QueueWidget(CommandManager commandManager, Message message, AudioManager audioManager, AudioPlayback audioPlayback) {
        super(commandManager, message);
        this.audioManager = audioManager;
        this.audioPlayback = audioPlayback;
    }

    @Override
    public List<AbstractWidgetAction> setupActions() {
        List<AbstractWidgetAction> actions = Lists.newArrayList();

        actions.add(shuffleAction());
        actions.add(new RewindAction(audioPlayback, audioManager));
        actions.add(new PlayPauseAction(audioPlayback, audioManager, true));
        actions.add(new SkipAction(audioPlayback, audioManager));
        actions.add(repeatAllAction());
        actions.add(repeatOneAction());

        return actions;
    }

    @Override
    public void reset() throws Exception {
        MessageService messageService = Botify.get().getMessageService();
        Message message = getMessage();
        try {
            message.delete().queue();
            setMessageDeleted(true);
        } catch (InsufficientPermissionException e) {
            messageService.sendError("Bot is missing permission: " + e.getPermission().getName(), message.getChannel());
        } catch (Throwable e) {
            OffsetDateTime timeCreated = message.getTimeCreated();
            ZonedDateTime zonedDateTime = timeCreated.atZoneSameInstant(ZoneId.systemDefault());
            LoggerFactory.getLogger(getClass()).warn(String.format("Cannot delete queue widget message from %s for channel %s on guild %s",
                zonedDateTime, message.getChannel(), message.getGuild()), e);
        }

        EmbedBuilder embedBuilder = audioPlayback.getAudioQueue().buildMessageEmbed(audioPlayback, message.getGuild());
        CompletableFuture<Message> futureMessage = messageService.send(embedBuilder, message.getChannel());
        getCommandManager().registerWidget(new QueueWidget(getCommandManager(), futureMessage.get(), audioManager, audioPlayback));
    }

    private AbstractWidgetAction shuffleAction() {
        return new AbstractWidgetAction("shuffle", EmojiConstants.SHUFFLE, true) {
            @Override
            protected void handleReaction(GuildMessageReactionAddEvent event) {
                audioPlayback.setShuffle(!audioPlayback.isShuffle());
            }
        };
    }

    private AbstractWidgetAction repeatAllAction() {
        return new AbstractWidgetAction("repeat", EmojiConstants.REPEAT, true) {
            @Override
            protected void handleReaction(GuildMessageReactionAddEvent event) {
                audioPlayback.setRepeatAll(!audioPlayback.isRepeatAll());
            }
        };
    }

    private AbstractWidgetAction repeatOneAction() {
        return new AbstractWidgetAction("repeat", EmojiConstants.REPEAT_ONE, true) {
            @Override
            protected void handleReaction(GuildMessageReactionAddEvent event) {
                audioPlayback.setRepeatOne(!audioPlayback.isRepeatOne());
            }
        };
    }

}
