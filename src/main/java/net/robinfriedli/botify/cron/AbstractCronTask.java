package net.robinfriedli.botify.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.jxp.exec.BaseInvoker;
import net.robinfriedli.jxp.exec.Invoker;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

public abstract class AbstractCronTask implements Job {

    private final Invoker invoker;
    private final Logger logger;

    public AbstractCronTask() {
        invoker = new BaseInvoker();
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        try {
            invoker.invoke(getMode(), () -> {
                run(jobExecutionContext);
                return null;
            });
        } catch (Throwable e) {
            logger.error("Error in cron job", e);
        }
    }

    protected abstract void run(JobExecutionContext jobExecutionContext) throws Exception;

    protected abstract Invoker.Mode getMode();

}
