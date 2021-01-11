package net.robinfriedli.botify.discord.listeners;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.concurrent.EventHandlerPool;
import org.springframework.stereotype.Component;

@Component
public class EventWaiter extends ListenerAdapter {

    @SuppressWarnings("rawtypes")
    private final Multimap<Class<? extends GenericEvent>, AwaitedEvent> awaitedEventMap = LinkedHashMultimap.create();
    private final ReentrantReadWriteLock mapLock = new ReentrantReadWriteLock();

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void onGenericEvent(@Nonnull GenericEvent event) {
        if (awaitedEventMap.isEmpty()) {
            return;
        }

        EventHandlerPool.execute(() -> {
            ReentrantReadWriteLock.ReadLock readLock = mapLock.readLock();
            readLock.lock();
            try {
                List<AwaitedEvent> awaitedEvents = awaitedEventMap.get(event.getClass())
                    .stream()
                    .filter(awaitedEvent -> awaitedEvent.getFilterPredicate().test(event))
                    .collect(Collectors.toList());

                if (!awaitedEvents.isEmpty()) {
                    awaitedEvents.forEach(awaitedEvent -> {
                        // completing the future might trigger afterCompletion handles in the current thread
                        awaitedEvent.getCompletableFuture().complete(event);
                        awaitedEventMap.remove(awaitedEvent.getEventType(), awaitedEvent);
                    });
                }
            } finally {
                readLock.unlock();
            }
        });
    }

    public <E extends GenericEvent> E awaitEvent(Class<E> eventType, Predicate<E> predicate, long timeout, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
        CompletableFuture<E> completableFuture = awaitEvent(eventType, predicate);
        try {
            return completableFuture.get(timeout, timeUnit);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public <E extends GenericEvent> CompletableFuture<E> awaitEvent(Class<E> eventType, Predicate<E> predicate) {
        ReentrantReadWriteLock.WriteLock writeLock = mapLock.writeLock();
        writeLock.lock();
        try {
            AwaitedEvent<E> awaitedEvent = new AwaitedEvent<>(eventType, predicate);
            awaitedEventMap.put(eventType, awaitedEvent);
            return awaitedEvent.getCompletableFuture();
        } finally {
            writeLock.unlock();
        }
    }

    private static class AwaitedEvent<E extends GenericEvent> {

        private final Class<E> eventType;
        private final Predicate<E> filterPredicate;
        private final CompletableFuture<E> completableFuture;

        private AwaitedEvent(Class<E> eventType, Predicate<E> filterPredicate) {
            this.eventType = eventType;
            this.filterPredicate = filterPredicate;
            completableFuture = new CompletableFuture<>();
        }

        public Class<E> getEventType() {
            return eventType;
        }

        public Predicate<E> getFilterPredicate() {
            return filterPredicate;
        }

        public CompletableFuture<E> getCompletableFuture() {
            return completableFuture;
        }
    }

}
