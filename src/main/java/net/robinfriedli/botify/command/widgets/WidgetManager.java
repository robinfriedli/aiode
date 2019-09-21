package net.robinfriedli.botify.command.widgets;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Lists;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.exceptions.UserException;

public class WidgetManager {

    /**
     * all widgets that currently listen for reactions. Only one widget of the same type per guild may be active.
     */
    private final List<AbstractWidget> activeWidgets = Lists.newArrayList();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public synchronized void registerWidget(AbstractWidget widget) {
        List<AbstractWidget> toRemove = com.google.common.collect.Lists.newArrayList();
        try {
            activeWidgets.stream()
                .filter(w -> widget.getGuildId().equals(w.getGuildId()))
                .filter(w -> w.getClass().equals(widget.getClass()))
                .forEach(toRemove::add);
        } catch (Throwable e) {
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
