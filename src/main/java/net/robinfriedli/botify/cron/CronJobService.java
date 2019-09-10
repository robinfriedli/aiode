package net.robinfriedli.botify.cron;

import java.util.List;

import net.robinfriedli.botify.entities.xml.CronJobContribution;
import net.robinfriedli.botify.util.Cache;
import net.robinfriedli.jxp.persist.Context;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public class CronJobService {

    private final Context contributionContext;
    private final Scheduler scheduler;

    public CronJobService(Context contributionContext) throws SchedulerException {
        this.contributionContext = contributionContext;
        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
    }

    public void scheduleAll() throws SchedulerException {
        List<CronJobContribution> contributions = contributionContext.getInstancesOf(CronJobContribution.class);
        for (CronJobContribution contribution : contributions) {
            JobDataMap jobDataMap = new JobDataMap();
            List<CronJobContribution.CronParameter> parameters = contribution.getInstancesOf(CronJobContribution.CronParameter.class);
            for (CronJobContribution.CronParameter parameter : parameters) {
                jobDataMap.put(parameter.getName(), Cache.get(parameter.getImplementationClass()));
            }

            JobDetail jobDetail = JobBuilder.newJob(contribution.getImplementationClass())
                .withIdentity(contribution.getId())
                .setJobData(jobDataMap)
                .build();

            CronTrigger trigger = TriggerBuilder
                .newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(contribution.getCronExpression()))
                .build();
            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

}
