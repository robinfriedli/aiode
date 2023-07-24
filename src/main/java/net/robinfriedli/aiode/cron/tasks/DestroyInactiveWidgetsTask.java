package net.robinfriedli.aiode.cron.tasks;

import java.time.Duration;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.cron.AbstractCronTask;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.function.RateLimitInvoker;
import net.robinfriedli.exec.Mode;
import org.quartz.JobExecutionContext;

public class DestroyInactiveWidgetsTask extends AbstractCronTask {

    private static final RateLimitInvoker WIDGET_DESTROY_RATE_LIMITED = new RateLimitInvoker("inactive_widget_destruction", 5, Duration.ofSeconds(1));

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected void run(JobExecutionContext jobExecutionContext) throws Exception {
        Aiode aiode = Aiode.get();
        GuildManager guildManager = aiode.getGuildManager();

        Set<AbstractWidget> widgetsToDestroy = Sets.newHashSet();
        for (GuildContext guildContext : guildManager.getGuildContexts()) {
            WidgetRegistry widgetRegistry = guildContext.getWidgetRegistry();
            widgetRegistry.withActiveWidgets(activeWidgets ->
                activeWidgets.stream().filter(AbstractWidget::isInactive).forEach(widgetsToDestroy::add)
            );
        }

        if (!widgetsToDestroy.isEmpty()) {
            logger.info("Found {} inactive widgets to destroy", widgetsToDestroy.size());
            for (AbstractWidget widgetToDestroy : widgetsToDestroy) {
                if (widgetToDestroy.isInactive()) {
                    WIDGET_DESTROY_RATE_LIMITED.invokeLimited(widgetToDestroy::destroy);
                }
            }
        }
    }

    @Override
    protected Mode getMode() {
        return Mode.create();
    }

}
