package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class SkipCommand extends AbstractCommand {

    public SkipCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, false, false, identifier,
            "Skip to the next track in the queue.", Category.PLAYBACK);
    }

    @Override
    public void doRun() {
        AudioManager audioManager = getManager().getAudioManager();
        Guild guild = getContext().getGuild();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        AudioQueue queue = playback.getAudioQueue();
        VoiceChannel channel = guild.getMember(getContext().getUser()).getVoiceState().getChannel();

        if (!(queue.hasNext() || playback.isRepeatAll())) {
            throw new InvalidCommandException("No next item in queue");
        }

        if (playback.isRepeatAll() && !queue.hasNext()) {
            queue.reset();
        } else {
            queue.next();
        }
        audioManager.playTrack(guild, channel);
    }

    @Override
    public void onSuccess() {
    }
}
