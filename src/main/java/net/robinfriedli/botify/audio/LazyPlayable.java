package net.robinfriedli.botify.audio;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LazyPlayable extends Playable {

    public LazyPlayable(AudioManager audioManager, CompletableFuture<Object> delegate) {
        super(audioManager, delegate);
    }

    @Override
    public Object delegate() {
        CompletableFuture<Object> futureDelegate = getFutureDelegate();
        try {
            return futureDelegate.get(3, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Loading playable timed out", e);
        }
    }

    public void complete(Object object) {
        getFutureDelegate().complete(object);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Object> getFutureDelegate() {
        return (CompletableFuture<Object>) super.delegate();
    }

}
