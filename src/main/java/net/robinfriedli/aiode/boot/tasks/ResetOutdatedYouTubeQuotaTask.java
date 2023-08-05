package net.robinfriedli.aiode.boot.tasks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.annotation.Nullable;

import jakarta.persistence.LockModeType;
import net.dv8tion.jda.api.JDA;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.entities.CurrentYouTubeQuotaUsage;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.aiode.persist.StaticSessionProvider;

/**
 * Resets the daily YouTube API quota counter on startup if it hasn't already been reset today i.e. if the bot was offline.
 */
public class ResetOutdatedYouTubeQuotaTask implements StartupTask {

    private final StartupTaskContribution contribution;
    private final YouTubeService youTubeService;

    public ResetOutdatedYouTubeQuotaTask(StartupTaskContribution contribution, YouTubeService youTubeService) {
        this.contribution = contribution;
        this.youTubeService = youTubeService;
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) {
        StaticSessionProvider.consumeSession(session -> {
            CurrentYouTubeQuotaUsage currentQuotaUsage = YouTubeService.getCurrentQuotaUsage(session, LockModeType.PESSIMISTIC_WRITE);
            LocalDateTime lastUpdatedNoZone = currentQuotaUsage.getLastUpdated();

            if (lastUpdatedNoZone != null) {
                ZoneId pstTime = ZoneId.of("PST", ZoneId.SHORT_IDS);
                LocalDate currentDate = LocalDate.now(pstTime);
                ZonedDateTime midnight = LocalDateTime.of(currentDate, LocalTime.MIDNIGHT).atZone(pstTime);
                ZonedDateTime lastUpdated = lastUpdatedNoZone.atZone(ZoneId.systemDefault());

                if (midnight.compareTo(lastUpdated) > 0) {
                    youTubeService.setAtomicQuotaUsage(0);
                    currentQuotaUsage.setQuota(0);
                }
            }
        });
    }
}
