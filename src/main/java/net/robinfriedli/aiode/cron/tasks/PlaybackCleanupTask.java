package net.robinfriedli.aiode.cron.tasks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.cron.AbstractCronTask;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.exceptions.DiscordEntityInitialisationException;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.exec.Mode;
import org.quartz.JobExecutionContext;

/**
 * Task that stops stale playbacks where the bot has been alone in a voice channel for over an hour and clears the
 * queue and resets playbacks for guilds that have been inactive for over an hour
 */
public class PlaybackCleanupTask extends AbstractCronTask {

    @Override
    public void run(JobExecutionContext jobExecutionContext) {
        Logger logger = LoggerFactory.getLogger(getClass());
        Aiode aiode = Aiode.get();
        GuildManager guildManager = aiode.getGuildManager();
        Set<GuildContext> guildContexts = guildManager.getGuildContexts();

        Set<Guild> activeGuilds = StaticSessionProvider.invokeWithSession(session ->
            guildManager.getActiveGuilds(session, 3600000));

        int clearedAlone = 0;
        int playbacksCleared = 0;

        for (GuildContext guildContext : guildContexts) {
            try {
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

                if (!activeGuilds.contains(playback.getGuild()) && !playback.isPlaying()) {
                    if (playback.clear()) {
                        ++playbacksCleared;
                    }
                }
            } catch (DiscordEntityInitialisationException e) {
                // guild could not be loaded anymore, skip
                continue;
            }
        }

        if (clearedAlone > 0 || playbacksCleared > 0) {
            logger.info(String.format("Cleared %d stale playbacks and stopped %d lone playbacks", playbacksCleared, clearedAlone));
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
    protected Mode getMode() {
        return Mode.create();
    }
}
