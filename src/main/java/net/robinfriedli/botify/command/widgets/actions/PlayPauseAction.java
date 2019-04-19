package net.robinfriedli.botify.command.widgets.actions;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.widgets.ActionRunable;

public class PlayPauseAction implements ActionRunable {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public PlayPauseAction(AudioPlayback audioPlayback, AudioManager audioManager) {
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    public void run(GuildMessageReactionAddEvent event) {
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
