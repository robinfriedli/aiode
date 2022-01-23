package net.robinfriedli.aiode.cron;

import java.io.IOException;
import java.util.List;

import org.slf4j.LoggerFactory;

import net.robinfriedli.aiode.boot.AbstractShutdownable;
import net.robinfriedli.aiode.entities.xml.CronJobContribution;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * service that schedules all cron tasks registered in the cronJobs.xml file and starts and shuts down the quartz
 * {@link Scheduler}, waiting for current tasks to finish on shut down.
 */
@Component
public class CronJobService extends AbstractShutdownable {

    private final boolean mainInstance;
    private final Context contributionContext;
    private final Scheduler scheduler;

    public CronJobService(
        @Value("${aiode.preferences.main_instance:true}") boolean mainInstance,
        @Value("classpath:xml-contributions/cronJobs.xml") Resource commandResource,
        JxpBackend jxpBackend
    ) throws SchedulerException {
        this.mainInstance = mainInstance;
        try {
            this.contributionContext = jxpBackend.createContext(commandResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(0)));
    }

    public void scheduleAll() throws SchedulerException {
        List<CronJobContribution> contributions = contributionContext.getInstancesOf(CronJobContribution.class);
        for (CronJobContribution contribution : contributions) {
            if (!mainInstance && contribution.getAttribute("mainInstanceOnly").getBool()) {
                continue;
            }

            JobDetail jobDetail = JobBuilder.newJob(contribution.getImplementationClass())
                .withIdentity(contribution.getId())
                .build();

            CronTrigger trigger = TriggerBuilder
                .newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(contribution.getCronExpression()).inTimeZone(contribution.getTimeZone()))
                .build();
            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

    @Override
    public void shutdown(int delay) {
        try {
            scheduler.shutdown(true);
        } catch (SchedulerException e) {
            LoggerFactory.getLogger(getClass()).error("Error while shutting down cron service", e);
        }
    }

}
