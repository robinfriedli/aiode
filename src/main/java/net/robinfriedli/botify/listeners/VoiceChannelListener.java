package net.robinfriedli.botify.listeners;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.properties.AbstractGuildProperty;
import net.robinfriedli.botify.discord.properties.GuildPropertyManager;

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
            if (channel.equals(playback.getVoiceChannel())
                && noOtherMembersLeft(channel, guild)
                && isAutoPauseEnabled(guild)) {
                playback.pause();
                audioManager.leaveChannel(playback);
            }
        }
    }

    private boolean noOtherMembersLeft(VoiceChannel channel, Guild guild) {
        return channel.getMembers().stream()
            .allMatch(member -> member.equals(guild.getSelfMember()));
    }

    private boolean isAutoPauseEnabled(Guild guild) {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
        GuildManager guildManager = Botify.get().getGuildManager();
        GuildContext guildContext = guildManager.getContextForGuild(guild);
        AbstractGuildProperty enableAutoPauseProperty = guildPropertyManager.getProperty("enableAutoPause");
        if (enableAutoPauseProperty != null) {
            return (boolean) enableAutoPauseProperty.get(guildContext);
        }

        return true;
    }

}
