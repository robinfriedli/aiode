package net.robinfriedli.botify.command.widgets;

import java.util.List;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.entities.Message;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.widgets.actions.PlayPauseAction;
import net.robinfriedli.botify.command.widgets.actions.RewindAction;
import net.robinfriedli.botify.command.widgets.actions.SkipAction;

public class NowPlayingWidget extends AbstractWidget {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public NowPlayingWidget(CommandManager commandManager, Message message, AudioPlayback audioPlayback, AudioManager audioManager) {
        super(commandManager, message);
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    public List<AbstractWidgetAction> setupActions() {
        List<AbstractWidgetAction> actions = Lists.newArrayList();

        actions.add(new RewindAction(audioPlayback, audioManager));
        actions.add(new PlayPauseAction(audioPlayback, audioManager));
        actions.add(new SkipAction(audioPlayback, audioManager));

        return actions;
    }

    @Override
    public void reset() {
        setMessageDeleted(true);
        // if a different track is played after using the skip or rewind action, the old "now playing" message will get
        // deleted by the AudioPlayback anyway
    }

    @Override
    public void destroy() {
        getCommandManager().removeWidget(this);
    }
}
