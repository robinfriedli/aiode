package net.robinfriedli.botify.command.widgets;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Lists;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.xml.WidgetContribution;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Manager that holds active widgets, each {@link GuildContext} has one instance of this manager
 */
public class WidgetManager {

    /**
     * all widgets that currently listen for reactions. Only one widget of the same type per guild may be active.
     */
    private final List<AbstractWidget> activeWidgets = Lists.newArrayList();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Context widgetConfigurationContext;

    public WidgetManager() {
        Botify botify = Botify.get();
        JxpBackend jxpBackend = botify.getJxpBackend();
        InputStream widgetsResource = getClass().getResourceAsStream("/xml-contributions/widgets.xml");
        widgetConfigurationContext = jxpBackend.createContext(widgetsResource);
    }

    public WidgetContribution getContributionForWidget(Class<? extends AbstractWidget> type) {
        WidgetContribution widgetContribution = widgetConfigurationContext.query(and(
            attribute("implementation").is(type.getName()),
            instanceOf(WidgetContribution.class)
        ), WidgetContribution.class).getOnlyResult();

        if (widgetContribution == null) {
            throw new IllegalStateException("No widget configuration for " + type + ". Add to widgets.xml");
        }

        return widgetContribution;
    }

    public synchronized void registerWidget(AbstractWidget widget) {
        List<AbstractWidget> toRemove = Lists.newArrayList();
        try {
            activeWidgets.stream()
                .filter(w -> widget.getGuildId().equals(w.getGuildId()))
                .filter(w -> w.getClass().equals(widget.getClass()))
                .forEach(toRemove::add);
        } catch (Exception e) {
            logger.warn("Exception while removing existing widget", e);
        }
        try {
            widget.setup();
            activeWidgets.add(widget);
            toRemove.forEach(AbstractWidget::destroy);
        } catch (UserException e) {
            Botify.get().getMessageService().sendError(e.getMessage(), widget.getMessage().getChannel());
        }
    }

    public synchronized Optional<AbstractWidget> getActiveWidget(String messageId) {
        return activeWidgets.stream().filter(widget -> widget.getMessage().getId().equals(messageId)).findAny();
    }

    public synchronized void removeWidget(AbstractWidget widget) {
        activeWidgets.remove(widget);
    }

}
