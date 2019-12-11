package net.robinfriedli.botify.cron.tasks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.cron.AbstractCronTask;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.jxp.exec.Invoker;
import org.quartz.JobExecutionContext;

/**
 * Task that stops stale playbacks where the bot has been alone in a voice channel for over an hour and clears the
 * queue and resets playbacks for guilds that have been inactive for over an hour
 */
public class PlaybackCleanupTask extends AbstractCronTask {

    @Override
    public void run(JobExecutionContext jobExecutionContext) {
        Logger logger = LoggerFactory.getLogger(getClass());
        Botify botify = Botify.get();
        GuildManager guildManager = botify.getGuildManager();
        Set<GuildContext> guildContexts = guildManager.getGuildContexts();

        Set<Guild> activeGuilds = StaticSessionProvider.invokeWithSession(session -> {
            return guildManager.getActiveGuilds(session, 3600000);
        });

        int clearedAlone = 0;
        int playbacksCleared = 0;

        for (GuildContext guildContext : guildContexts) {
            AudioPlayback playback = guildContext.getPlayback();
            LocalDateTime aloneSince = playback.getAloneSince();
            if (aloneSince != null) {
                Duration aloneSinceDuration = Duration.between(aloneSince, LocalDateTime.now());
                Duration oneHour = Duration.ofHours(1);
                if (aloneSinceDuration.compareTo(oneHour) > 0) {
                    if (clearLonePlayback(guildContext, playback)) {
                        ++clearedAlone;
                        continue;
                    }
                }
            }

            if (!activeGuilds.contains(playback.getGuild())) {
                if (playback.clear()) {
                    ++playbacksCleared;
                }
            }
        }

        if (clearedAlone > 0 || playbacksCleared > 0) {
            logger.info(String.format("Cleared %s stale playbacks and stopped %s lone playbacks", playbacksCleared, clearedAlone));
        }
    }

    private boolean clearLonePlayback(GuildContext guildContext, AudioPlayback playback) {
        VoiceChannel voiceChannel = playback.getVoiceChannel();
        Guild guild = guildContext.getGuild();

        if (voiceChannel == null) {
            return false;
        }

        boolean isAlone = voiceChannel.getMembers().stream().allMatch(member -> member.equals(guild.getSelfMember()) || member.getUser().isBot());
        if (isAlone) {
            playback.stop();
            playback.setAloneSince(null);
            playback.clear();
            return true;
        }

        return false;
    }

    @Override
    protected Invoker.Mode getMode() {
        return Invoker.Mode.create();
    }
}
