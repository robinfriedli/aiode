package net.robinfriedli.botify.listeners;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import net.robinfriedli.botify.util.StaticSessionProvider;

/**
 * Listener responsible for listening for VoiceChannel events; currently used for the auto pause feature
 */
public class VoiceChannelListener extends ListenerAdapter {

    private final AudioManager audioManager;
    private final ExecutorService executorService;

    public VoiceChannelListener(AudioManager audioManager) {
        this.audioManager = audioManager;
        executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
        if (!event.getMember().getUser().isBot()) {
            executorService.execute(() -> {
                VoiceChannel channel = event.getChannelLeft();
                Guild guild = event.getGuild();
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (channel.equals(playback.getVoiceChannel())
                    && noOtherMembersLeft(channel, guild)) {
                    if (isAutoPauseEnabled(guild)) {
                        playback.pause();
                        audioManager.leaveChannel(playback);
                    } else {
                        playback.setAloneSince(LocalDateTime.now());
                    }
                }
            });
        }
    }

    @Override
    public void onGuildVoiceJoin(@Nonnull GuildVoiceJoinEvent event) {
        if (!event.getMember().getUser().isBot()) {
            executorService.execute(() -> {
                Guild guild = event.getGuild();
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                if (event.getChannelJoined().equals(playback.getVoiceChannel())) {
                    playback.setAloneSince(null);
                }
            });
        }
    }

    private boolean noOtherMembersLeft(VoiceChannel channel, Guild guild) {
        return channel.getMembers().stream()
            .allMatch(member -> member.equals(guild.getSelfMember()));
    }

    private boolean isAutoPauseEnabled(Guild guild) {
        return StaticSessionProvider.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            GuildManager guildManager = Botify.get().getGuildManager();
            GuildSpecification specification = guildManager.getContextForGuild(guild).getSpecification(session);
            AbstractGuildProperty enableAutoPauseProperty = guildPropertyManager.getProperty("enableAutoPause");
            if (enableAutoPauseProperty != null) {
                return (boolean) enableAutoPauseProperty.get(specification);
            }

            return true;
        });
    }

}
