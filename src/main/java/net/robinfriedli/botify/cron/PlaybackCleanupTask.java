package net.robinfriedli.botify.cron;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.util.StaticSessionProvider;
import org.quartz.JobExecutionContext;

public class PlaybackCleanupTask extends AbstractCronTask {

    @Override
    public void run(JobExecutionContext jobExecutionContext) {
        Logger logger = LoggerFactory.getLogger(getClass());
        logger.info("Starting playback cleanup");
        AudioManager audioManager = getParameter(AudioManager.class);
        GuildManager guildManager = getParameter(GuildManager.class);
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
                    audioManager.leaveChannel(playback);
                    playback.setAloneSince(null);
                    playback.clear();
                    ++clearedAlone;
                }
            }
        }

        Set<Guild> activeGuilds = StaticSessionProvider.invokeWithSession(session -> {
            return guildManager.getActiveGuilds(session, 3600000);
        });

        for (GuildContext guildContext : guildContexts) {
            AudioPlayback playback = guildContext.getPlayback();
            if (!activeGuilds.contains(playback.getGuild())) {
                playback.clear();
            }
        }

        if (clearedAlone > 0) {
            logger.info("Stopped " + clearedAlone + " lone playbacks.");
        }
    }
}
