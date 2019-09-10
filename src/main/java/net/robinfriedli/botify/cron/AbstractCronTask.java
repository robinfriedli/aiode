package net.robinfriedli.botify.cron;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

public abstract class AbstractCronTask implements Job {

    private final Logger logger;
    private JobExecutionContext jobExecutionContext;

    public AbstractCronTask() {
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        this.jobExecutionContext = jobExecutionContext;
        try {
            run(jobExecutionContext);
        } catch (Throwable e) {
            logger.error("Error in cron job", e);
        }
    }

    protected abstract void run(JobExecutionContext jobExecutionContext) throws Exception;

    @SuppressWarnings("unchecked")
    protected <E> E getParameter(Class<E> type) {
        JobDataMap jobDataMap = jobExecutionContext.getMergedJobDataMap();
        Optional<Object> mappedParameter = jobDataMap.values().stream().filter(type::isInstance).findFirst();
        return (E) mappedParameter.orElseThrow();
    }

}
