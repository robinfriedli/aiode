package net.robinfriedli.botify.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.function.modes.HibernateTransactionMode;
import net.robinfriedli.botify.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.exec.Invoker;
import net.robinfriedli.exec.Mode;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * Abstract class for any scheduled cron task
 */
public abstract class AbstractCronTask implements Job {

    private final Invoker invoker;
    private final Logger logger;

    public AbstractCronTask() {
        invoker = Invoker.newInstance();
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        try {
            invoker.invoke(getMode(), () -> {
                run(jobExecutionContext);
                return null;
            });
        } catch (Exception e) {
            logger.error("Error in cron job", e);
        }
    }

    /**
     * The logic to run
     *
     * @param jobExecutionContext the execution context provided by quartz
     * @throws Exception any exception thrown during execution
     */
    protected abstract void run(JobExecutionContext jobExecutionContext) throws Exception;

    /**
     * The {@link Mode} to invoke the run method with. For example, use the {@link HibernateTransactionMode}
     * if the task needs to run in a hibernate transaction, or {@link SpotifyAuthorizationMode} if the task needs to run
     * with Spotify client credentials set up.
     *
     * @return the mode to use
     */
    protected abstract Mode getMode();

}
