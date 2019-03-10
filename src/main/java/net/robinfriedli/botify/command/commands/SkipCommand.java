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

    public SkipCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, false, identifier, description, Category.PLAYBACK);
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

        if (getCommandBody().isBlank()) {
            if (playback.isRepeatAll() && !queue.hasNext()) {
                queue.reset();
            } else {
                queue.next();
            }
        } else {
            int offset;
            try {
                offset = Integer.parseInt(getCommandBody());
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(getCommandBody() + " is not an integer");
            }

            if (offset < 1) {
                throw new InvalidCommandException("Expected a number greater than 0");
            }

            int newIndex;
            int queueSize = queue.getTracks().size();
            boolean overflow = queue.getPosition() + offset >= queueSize;
            if (!playback.isRepeatAll() && overflow) {
                newIndex = queueSize - 1;
            } else if (overflow) {
                // if the current index is 30 with 50 tracks in the queue and the user wants to skip 24, the result should be 4
                // if the user wants to skip 20, the result be 0
                int provisional = queue.getPosition() + offset;
                int page = provisional / queueSize;
                newIndex = provisional - (page * queueSize);
            } else {
                newIndex = queue.getPosition() + offset;
            }

            queue.setPosition(newIndex);
        }
        audioManager.playTrack(guild, channel);
    }

    @Override
    public void onSuccess() {
    }
}
