package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;

public class PauseCommand extends AbstractCommand {

    public PauseCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, false,
            "Pause the current playback.");
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioPlayback playback = getManager().getAudioManager().getPlaybackForGuild(guild);
        playback.pause();
    }

    @Override
    public void onSuccess() {
    }

}
