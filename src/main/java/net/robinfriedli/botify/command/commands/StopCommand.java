package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;

public class StopCommand extends AbstractCommand {

    public StopCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = getManager().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        playback.interruptTrackLoading();
        playback.stop();
        playback.getAudioQueue().clear();
        audioManager.leaveChannel(playback);
    }

    @Override
    public void onSuccess() {
    }
}
