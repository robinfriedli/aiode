package net.robinfriedli.botify.discord.listeners;

import java.time.LocalDateTime;

import javax.annotation.Nonnull;

import com.antkorwin.xsync.XSync;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.concurrent.EventHandlerPool;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import org.springframework.stereotype.Component;

/**
 * Listener responsible for listening for VoiceChannel events; currently used for the auto pause feature
 */
@Component
public class VoiceChannelListener extends ListenerAdapter {

    private final AudioManager audioManager;
    private final HibernateComponent hibernateComponent;
    private final XSync<Long> xSync;

    public VoiceChannelListener(AudioManager audioManager, HibernateComponent hibernateComponent) {
        this.audioManager = audioManager;
        this.hibernateComponent = hibernateComponent;
        this.xSync = new XSync<>();
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
        if (!event.getMember().getUser().isBot()) {
            Guild guild = event.getGuild();
            EventHandlerPool.execute(() -> xSync.execute(guild.getIdLong(), () -> {
                VoiceChannel channel = event.getChannelLeft();
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (channel.equals(playback.getVoiceChannel())
                    && noOtherMembersLeft(channel, guild)) {
                    if (isAutoPauseEnabled(guild)) {
                        playback.pause();
                        playback.leaveChannel();
                    } else {
                        playback.setAloneSince(LocalDateTime.now());
                    }
                }
            }));
        }
    }

    @Override
    public void onGuildVoiceJoin(@Nonnull GuildVoiceJoinEvent event) {
        if (!event.getMember().getUser().isBot()) {
            Guild guild = event.getGuild();
            EventHandlerPool.execute(() -> xSync.execute(guild.getIdLong(), () -> {
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (event.getChannelJoined().equals(playback.getVoiceChannel())) {
                    playback.setAloneSince(null);
                }
            }));
        }
    }

    private boolean noOtherMembersLeft(VoiceChannel channel, Guild guild) {
        return channel.getMembers().stream()
            .allMatch(member -> member.equals(guild.getSelfMember()) || member.getUser().isBot());
    }

    private boolean isAutoPauseEnabled(Guild guild) {
        return hibernateComponent.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            GuildManager guildManager = Botify.get().getGuildManager();
            GuildSpecification specification = guildManager.getContextForGuild(guild).getSpecification(session);

            return guildPropertyManager
                .getPropertyValueOptional("enableAutoPause", Boolean.class, specification)
                .orElse(true);
        });
    }
}
