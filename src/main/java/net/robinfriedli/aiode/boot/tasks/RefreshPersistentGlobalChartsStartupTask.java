package net.robinfriedli.aiode.boot.tasks;

import net.dv8tion.jda.api.JDA;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.ChartService;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import org.jetbrains.annotations.Nullable;

public class RefreshPersistentGlobalChartsStartupTask implements StartupTask {

    private final ChartService chartService;
    private final StartupTaskContribution contribution;

    public RefreshPersistentGlobalChartsStartupTask(ChartService chartService, StartupTaskContribution contribution) {
        this.chartService = chartService;
        this.contribution = contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) throws Exception {
        Thread persistentChartUpdateThread = new Thread(() -> {
            Aiode.LOGGER.info("Starting to refresh persistent global charts");
            long millis = System.currentTimeMillis();
            try {
                chartService.refreshPersistentGlobalCharts();
            } catch (Exception e) {
                Aiode.LOGGER.error("Error occurred refreshing persistent global charts", e);
            }
            Aiode.LOGGER.info("Completed refreshing persistent global charts after {}ms", System.currentTimeMillis() - millis);
        });
        persistentChartUpdateThread.setName("refresh-persistent-global-charts-thread");
        persistentChartUpdateThread.setDaemon(true);
        persistentChartUpdateThread.start();
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }
}
