package net.robinfriedli.botify.cron.tasks;

import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.cron.AbstractCronTask;
import net.robinfriedli.botify.entities.CurrentYouTubeQuotaUsage;
import net.robinfriedli.botify.function.modes.HibernateTransactionMode;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.jxp.exec.Invoker;
import org.quartz.JobExecutionContext;

/**
 * Reset the {@link CurrentYouTubeQuotaUsage} every midnight PT.
 */
public class ResetCurrentYouTubeQuotaTask extends AbstractCronTask {

    @Override
    protected void run(JobExecutionContext jobExecutionContext) {
        YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
        StaticSessionProvider.consumeSession(session -> {
            CurrentYouTubeQuotaUsage currentQuotaUsage = YouTubeService.getCurrentQuotaUsage(session);
            youTubeService.setAtomicQuotaUsage(0);
            currentQuotaUsage.setQuota(0);
        });
        LoggerFactory.getLogger(getClass()).info("Reset current YouTube API Quota counter");
    }

    @Override
    protected Invoker.Mode getMode() {
        return Invoker.Mode.create().with(new HibernateTransactionMode());
    }
}
