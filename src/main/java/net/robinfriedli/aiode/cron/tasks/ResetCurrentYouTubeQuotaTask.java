package net.robinfriedli.aiode.cron.tasks;

import org.slf4j.LoggerFactory;

import jakarta.persistence.LockModeType;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.cron.AbstractCronTask;
import net.robinfriedli.aiode.entities.CurrentYouTubeQuotaUsage;
import net.robinfriedli.aiode.function.modes.HibernateTransactionMode;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.exec.Mode;
import org.quartz.JobExecutionContext;

/**
 * Reset the {@link CurrentYouTubeQuotaUsage} every midnight PT.
 */
public class ResetCurrentYouTubeQuotaTask extends AbstractCronTask {

    @Override
    protected void run(JobExecutionContext jobExecutionContext) {
        YouTubeService youTubeService = Aiode.get().getAudioManager().getYouTubeService();
        StaticSessionProvider.consumeSession(session -> {
            CurrentYouTubeQuotaUsage currentQuotaUsage = YouTubeService.getCurrentQuotaUsage(session, LockModeType.PESSIMISTIC_WRITE);
            youTubeService.setAtomicQuotaUsage(0);
            currentQuotaUsage.setQuota(0);
        });
        LoggerFactory.getLogger(getClass()).info("Reset current YouTube API Quota counter");
    }

    @Override
    protected Mode getMode() {
        return Mode.create().with(new HibernateTransactionMode());
    }
}
