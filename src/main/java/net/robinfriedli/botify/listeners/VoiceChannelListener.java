package net.robinfriedli.botify.listeners;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;

public class VoiceChannelListener extends ListenerAdapter {

    private final AudioManager audioManager;

    public VoiceChannelListener(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
        if (!event.getMember().getUser().isBot()) {
            VoiceChannel channel = event.getChannelLeft();
            Guild guild = event.getGuild();
            AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
            if (channel.equals(playback.getVoiceChannel())) {
                boolean noOtherMembersLeft = channel.getMembers().stream()
                    .allMatch(member -> member.equals(guild.getSelfMember()));
                if (noOtherMembersLeft) {
                    playback.pause();
                    audioManager.leaveChannel(playback);
                }
            }
        }
    }
}
