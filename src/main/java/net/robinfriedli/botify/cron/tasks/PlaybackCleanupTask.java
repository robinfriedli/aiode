package net.robinfriedli.botify.cron.tasks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
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
        AudioManager audioManager = botify.getAudioManager();
        GuildManager guildManager = botify.getGuildManager();
        Collection<GuildContext> guildContexts = guildManager.getGuildContexts();

        int clearedAlone = 0;
        for (GuildContext guildContext : guildContexts) {
            AudioPlayback playback = guildContext.getPlayback();
            LocalDateTime aloneSince = playback.getAloneSince();
            if (aloneSince != null) {
                Duration aloneSinceDuration = Duration.between(aloneSince, LocalDateTime.now());
                Duration oneHour = Duration.ofHours(1);
                if (aloneSinceDuration.compareTo(oneHour) > 0) {
                    playback.stop();
                    playback.setAloneSince(null);
                    playback.clear();
                    ++clearedAlone;
                }
            }
        }

        Set<Guild> activeGuilds = StaticSessionProvider.invokeWithSession(session -> {
            return guildManager.getActiveGuilds(session, 3600000);
        });

        int playbacksCleared = 0;
        for (GuildContext guildContext : guildContexts) {
            AudioPlayback playback = guildContext.getPlayback();
            if (!activeGuilds.contains(playback.getGuild())) {
                if (playback.clear()) {
                    ++playbacksCleared;
                }
            }
        }

        logger.info(String.format("Cleared %s stale playbacks and stopped %s lone playbacks", playbacksCleared, clearedAlone));
    }

    @Override
    protected Invoker.Mode getMode() {
        return Invoker.Mode.create();
    }
}
