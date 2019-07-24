package net.robinfriedli.botify.command.widgets.actions;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.util.EmojiConstants;

public class PlayPauseAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public PlayPauseAction(AudioPlayback audioPlayback, AudioManager audioManager) {
        this(audioPlayback, audioManager, false);
    }

    public PlayPauseAction(AudioPlayback audioPlayback, AudioManager audioManager, boolean resetRequired) {
        super("play", EmojiConstants.PLAY_PAUSE, resetRequired);
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    protected void handleReaction(GuildMessageReactionAddEvent event) {
        if (audioPlayback.isPaused()) {
            audioPlayback.unpause();
        } else if (audioPlayback.isPlaying()) {
            audioPlayback.pause();
        } else if (!audioPlayback.getAudioQueue().isEmpty()) {
            User user = event.getUser();
            Guild guild = event.getGuild();
            audioManager.playTrack(guild, guild.getMember(user).getVoiceState().getChannel());
        }
    }
}
