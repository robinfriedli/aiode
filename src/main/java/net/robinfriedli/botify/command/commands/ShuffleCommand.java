package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;

public class ShuffleCommand extends AbstractCommand {

    public ShuffleCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        AudioPlayback playbackForGuild = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        playbackForGuild.setShuffle(!playbackForGuild.isShuffle());
    }

    @Override
    public void onSuccess() {
        AudioPlayback playbackForGuild = getManager().getAudioManager().getPlaybackForGuild(getContext().getGuild());
        sendMessage(getContext().getChannel(), "Set shuffle to " + playbackForGuild.isShuffle());
    }
}
