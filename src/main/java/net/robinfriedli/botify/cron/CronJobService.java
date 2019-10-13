package net.robinfriedli.botify.cron;

import java.util.List;

import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.entities.xml.CronJobContribution;
import net.robinfriedli.jxp.persist.Context;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 * service that schedules all cron tasks registered in the cronJobs.xml file and starts and shuts down the quartz
 * {@link Scheduler}, waiting for current tasks to finish on shut down.
 */
public class CronJobService {

    private final Context contributionContext;
    private final Scheduler scheduler;

    public CronJobService(Context contributionContext) throws SchedulerException {
        this.contributionContext = contributionContext;
        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new CronJobServiceShutdownHook()));
    }

    public void scheduleAll() throws SchedulerException {
        List<CronJobContribution> contributions = contributionContext.getInstancesOf(CronJobContribution.class);
        for (CronJobContribution contribution : contributions) {
            JobDetail jobDetail = JobBuilder.newJob(contribution.getImplementationClass())
                .withIdentity(contribution.getId())
                .build();

            CronTrigger trigger = TriggerBuilder
                .newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(contribution.getCronExpression()))
                .build();
            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

    private class CronJobServiceShutdownHook implements Runnable {

        @Override
        public void run() {
            try {
                scheduler.shutdown(true);
            } catch (SchedulerException e) {
                LoggerFactory.getLogger(getClass());
            }
        }
    }

}
