package net.robinfriedli.botify.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CompletableFutures {

    /**
     * Wrapper for {@link CompletableFuture#whenComplete(BiConsumer)} that handles exceptions thrown during execution of
     * the whenComplete consumer by passing them to the errorConsumer
     */
    public static <E> CompletableFuture<E> handleWhenComplete(CompletableFuture<E> completableFuture, BiConsumer<E, ? super Throwable> handle, Consumer<Throwable> errorConsumer) {
        return completableFuture.whenComplete((result, throwable) -> {
            try {
                handle.accept(result, throwable);
            } catch (Exception e) {
                errorConsumer.accept(e);
            }
        });
    }

}
