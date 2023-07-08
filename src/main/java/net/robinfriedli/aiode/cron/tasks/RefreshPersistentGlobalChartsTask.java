package net.robinfriedli.aiode.cron.tasks;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.ChartService;
import net.robinfriedli.aiode.cron.AbstractCronTask;
import net.robinfriedli.aiode.function.modes.HibernateTransactionMode;
import net.robinfriedli.exec.Mode;
import org.quartz.JobExecutionContext;

public class RefreshPersistentGlobalChartsTask extends AbstractCronTask {

    @Override
    protected void run(JobExecutionContext jobExecutionContext) throws Exception {
        ChartService chartService = Aiode.get().getChartService();
        Aiode.LOGGER.info("Starting to refresh persistent global charts");
        long millis = System.currentTimeMillis();
        chartService.refreshPersistentGlobalCharts();
        Aiode.LOGGER.info("Completed refreshing persistent global charts after {}ms", System.currentTimeMillis() - millis);
    }

    @Override
    protected Mode getMode() {
        return Mode.create().with(new HibernateTransactionMode());
    }
}
