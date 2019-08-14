package net.robinfriedli.botify.command.widgets.actions;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.util.EmojiConstants;

public class RewindAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public RewindAction(AudioPlayback audioPlayback, AudioManager audioManager) {
        super("rewind", EmojiConstants.REWIND, true);
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    protected void handleReaction(GuildMessageReactionAddEvent event) {
        AudioQueue queue = audioPlayback.getAudioQueue();
        if (!queue.isEmpty()) {
            if (queue.hasPrevious()) {
                queue.reverse();
            }

            Guild guild = event.getGuild();
            GuildVoiceState voiceState = event.getMember().getVoiceState();
            audioManager.startPlayback(guild, voiceState != null ? voiceState.getChannel() : null);
        }
    }
}
