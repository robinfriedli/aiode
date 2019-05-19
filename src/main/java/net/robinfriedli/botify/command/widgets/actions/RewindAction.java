package net.robinfriedli.botify.command.widgets.actions;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.widgets.ActionRunable;

public class RewindAction implements ActionRunable {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public RewindAction(AudioPlayback audioPlayback, AudioManager audioManager) {
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    public void run(GuildMessageReactionAddEvent event) {
        AudioQueue queue = audioPlayback.getAudioQueue();
        if (queue.hasPrevious()) {
            queue.reverse();
        }

        User user = event.getUser();
        Guild guild = event.getGuild();
        audioManager.playTrack(guild, guild.getMember(user).getVoiceState().getChannel());
    }
}
