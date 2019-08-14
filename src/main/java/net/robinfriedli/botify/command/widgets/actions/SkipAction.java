package net.robinfriedli.botify.command.widgets.actions;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.util.EmojiConstants;

public class SkipAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public SkipAction(AudioPlayback audioPlayback, AudioManager audioManager) {
        super("skip", EmojiConstants.SKIP, true);
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    protected void handleReaction(GuildMessageReactionAddEvent event) {
        AudioQueue queue = audioPlayback.getAudioQueue();
        if (!queue.isEmpty()) {
            Guild guild = event.getGuild();
            if (queue.hasNext()) {
                queue.iterate();
                GuildVoiceState voiceState = event.getMember().getVoiceState();
                audioManager.startPlayback(guild, voiceState != null ? voiceState.getChannel() : null);
            } else {
                audioPlayback.stop();
            }
        }
    }
}
