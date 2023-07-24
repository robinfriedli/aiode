package net.robinfriedli.aiode.command.widget;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import com.google.api.client.util.Lists;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.discord.DiscordEntity;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.exceptions.UserException;

/**
 * Manager that holds active widgets, each {@link GuildContext} has one instance of this manager
 */
public class WidgetRegistry {

    /**
     * all widgets that currently listen for reactions. Only one widget of the same type per guild may be active.
     */
    private final Map<Long, AbstractWidget> activeWidgets = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void registerWidget(AbstractWidget widget) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            List<AbstractWidget> toRemove = Lists.newArrayList();

            if (!widget.getWidgetContribution().allowMultipleActive()) {
                activeWidgets.values().stream()
                    .filter(w -> w.getClass().equals(widget.getClass()))
                    .forEach(toRemove::add);
            }

            try {
                DiscordEntity.Message message = widget.getMessage();

                if (message == null) {
                    throw new IllegalStateException("Message should have been set up by this point (after initialise() was called)");
                }

                activeWidgets.put(message.getId(), widget);
                toRemove.forEach(AbstractWidget::destroy);
            } catch (UserException e) {
                DiscordEntity.Message message = widget.getMessage();

                if (message != null) {
                    DiscordEntity.MessageChannel channel = message.getChannel();
                    MessageChannel retrievedChannel = channel.retrieve();

                    if (retrievedChannel != null) {
                        Aiode.get().getMessageService().sendError(e.getMessage(), retrievedChannel);
                        return;
                    }
                }

                throw e;
            }
        } finally {
            writeLock.unlock();
        }
    }

    public Optional<AbstractWidget> getActiveWidget(long messageId) {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        readLock.lock();
        try {
            return Optional.ofNullable(activeWidgets.get(messageId));
        } finally {
            readLock.unlock();
        }
    }

    public void removeWidget(AbstractWidget widget) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            activeWidgets.values().remove(widget);
        } finally {
            writeLock.unlock();
        }
    }

    public void withActiveWidgets(Consumer<Collection<AbstractWidget>> c) {
        ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        readLock.lock();
        try {
            c.accept(activeWidgets.values());
        } finally {
            readLock.unlock();
        }
    }

}
