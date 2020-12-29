package net.robinfriedli.botify.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CompletableFutures {

    /**
     * Wrapper for {@link CompletableFuture#whenComplete(BiConsumer)} that handles exceptions thrown during execution of
     * the whenComplete consumer by passing them to the errorConsumer and delegates callbacks to the {@link EventHandlerPool}.
     */
    public static <E> CompletableFuture<E> handleWhenComplete(CompletableFuture<E> completableFuture, BiConsumer<E, ? super Throwable> handle, Consumer<Throwable> errorConsumer) {
        return completableFuture.whenComplete((result, throwable) -> EventHandlerPool.execute(() -> {
            try {
                handle.accept(result, throwable);
            } catch (Exception e) {
                errorConsumer.accept(e);
            }
        }));
    }

    /**
     * Wrapper for {@link CompletableFuture#thenAccept(Consumer)} that delegates potentially blocking callbacks to the
     * {@link EventHandlerPool}. Exceptions in the tasks will be handled and logged by the pool's exception handler.
     */
    public static <E> CompletableFuture<Void> thenAccept(CompletableFuture<E> completableFuture, Consumer<? super E> consumer) {
        return completableFuture.thenAccept(result -> EventHandlerPool.execute(() -> consumer.accept(result)));
    }

}
