package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class RewindCommand extends AbstractCommand {

    public RewindCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, false,
            "Go back to the previous track in the queue");
    }

    @Override
    public void doRun() {
        AudioManager audioManager = getManager().getAudioManager();
        Guild guild = getContext().getGuild();
        AudioQueue queue = audioManager.getQueue(guild);
        VoiceChannel channel = guild.getMember(getContext().getUser()).getVoiceState().getChannel();

        if (!queue.hasPrevious()) {
            throw new InvalidCommandException("No previous item in queue");
        }

        queue.previous();
        audioManager.playTrack(guild, getContext().getChannel(), channel);
    }

    @Override
    public void onSuccess() {
    }
}
