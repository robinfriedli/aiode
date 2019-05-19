package net.robinfriedli.botify.command.widgets;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.widgets.actions.PlayPauseAction;
import net.robinfriedli.botify.command.widgets.actions.RewindAction;
import net.robinfriedli.botify.command.widgets.actions.SkipAction;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.UserException;

public class QueueWidget extends AbstractWidget {

    private final AudioManager audioManager;
    private final AudioPlayback audioPlayback;

    public QueueWidget(CommandManager commandManager, Message message, AudioManager audioManager, AudioPlayback audioPlayback) {
        super(commandManager, message);
        this.audioManager = audioManager;
        this.audioPlayback = audioPlayback;
    }

    @Override
    public List<WidgetAction> setupActions() {
        List<WidgetAction> actions = Lists.newArrayList();

        actions.add(new WidgetAction("566750833254072323", "\uD83D\uDD00", event -> shuffleAction(), true));
        actions.add(new WidgetAction("566718491353153546", "⏮", new RewindAction(audioPlayback, audioManager), true));
        actions.add(new WidgetAction("566709184054296600", "⏯", new PlayPauseAction(audioPlayback, audioManager), true));
        actions.add(new WidgetAction("566712183942283265", "⏭", new SkipAction(audioPlayback, audioManager), true));
        actions.add(new WidgetAction("566752769986265088", "\uD83D\uDD01", event -> repeatAllAction(), true));
        actions.add(new WidgetAction("566753052992864257", "\uD83D\uDD02", event -> repeatOneAction(), true));

        return actions;
    }

    @Override
    public void reset() throws Exception {
        setMessageDeleted(true);
        try {
            getMessage().delete().queue();
        } catch (InsufficientPermissionException e) {
            throw new UserException("Bot is missing permission: " + e.getPermission().getName(), e);
        }

        EmbedBuilder embedBuilder = audioPlayback.getAudioQueue().buildMessageEmbed(audioPlayback, getMessage().getGuild());
        MessageService messageService = new MessageService();
        CompletableFuture<Message> futureMessage = messageService.sendWithLogo(embedBuilder, getMessage().getChannel());
        getCommandManager().registerWidget(new QueueWidget(getCommandManager(), futureMessage.get(), audioManager, audioPlayback));
    }

    private void shuffleAction() {
        audioPlayback.setShuffle(!audioPlayback.isShuffle());
    }

    private void repeatAllAction() {
        audioPlayback.setRepeatAll(!audioPlayback.isRepeatAll());
    }

    private void repeatOneAction() {
        audioPlayback.setRepeatOne(!audioPlayback.isRepeatOne());
    }

}
