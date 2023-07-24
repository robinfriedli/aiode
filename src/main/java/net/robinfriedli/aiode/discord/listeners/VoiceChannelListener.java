package net.robinfriedli.aiode.discord.listeners;

import java.time.LocalDateTime;

import com.antkorwin.xsync.XSync;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.concurrent.EventHandlerPool;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.entities.GuildSpecification;
import org.jetbrains.annotations.NotNull;
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
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        AudioChannel oldValue = event.getOldValue();
        AudioChannel newValue = event.getNewValue();
        if (newValue == null && oldValue != null) {
            onGuildVoiceLeave(event.getGuild(), event.getMember(), oldValue);
        } else if (newValue != null) {
            onGuildVoiceJoin(event.getGuild(), event.getMember(), newValue);
        }
    }

    private void onGuildVoiceLeave(Guild guild, Member member, AudioChannel channelLeft) {
        if (!member.getUser().isBot()) {
            EventHandlerPool.execute(() -> xSync.execute(guild.getIdLong(), () -> {
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (channelLeft.equals(playback.getAudioChannel())
                    && noOtherMembersLeft(channelLeft, guild)) {
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

    private void onGuildVoiceJoin(Guild guild, Member member, AudioChannel channelJoined) {
        if (!member.getUser().isBot()) {
            EventHandlerPool.execute(() -> xSync.execute(guild.getIdLong(), () -> {
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (channelJoined.equals(playback.getAudioChannel())) {
                    playback.setAloneSince(null);
                }
            }));
        }
    }

    private boolean noOtherMembersLeft(AudioChannel channel, Guild guild) {
        return channel.getMembers().stream()
            .allMatch(member -> member.equals(guild.getSelfMember()) || member.getUser().isBot());
    }

    private boolean isAutoPauseEnabled(Guild guild) {
        return hibernateComponent.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Aiode.get().getGuildPropertyManager();
            GuildManager guildManager = Aiode.get().getGuildManager();
            GuildSpecification specification = guildManager.getContextForGuild(guild).getSpecification(session);

            return guildPropertyManager
                .getPropertyValueOptional("enableAutoPause", Boolean.class, specification)
                .orElse(true);
        });
    }
}
