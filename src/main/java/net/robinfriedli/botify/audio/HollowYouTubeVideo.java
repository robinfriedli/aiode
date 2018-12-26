package net.robinfriedli.botify.audio;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;

/**
 * YouTube video when the data has not been loaded yet.
 */
public class HollowYouTubeVideo implements YouTubeVideo {

    private final CompletableFuture<String> title;
    private final CompletableFuture<String> id;
    private final CompletableFuture<Long> duration;
    @Nullable
    private final Track redirectedSpotifyTrack;

    public HollowYouTubeVideo() {
        this.title = new CompletableFuture<>();
        this.id = new CompletableFuture<>();
        this.duration = new CompletableFuture<>();
        this.redirectedSpotifyTrack = null;
    }

    public HollowYouTubeVideo(@Nullable Track redirectedSpotifyTrack) {
        this.title = new CompletableFuture<>();
        this.id = new CompletableFuture<>();
        this.duration = new CompletableFuture<>();
        this.redirectedSpotifyTrack = redirectedSpotifyTrack;
    }

    @Override
    public String getTitle() {
        return getCompleted(title);
    }

    @Override
    public String getId() {
        return getCompleted(id);
    }

    @Override
    public long getDuration() {
        return getCompleted(duration);
    }

    @Nullable
    @Override
    public Track getRedirectedSpotifyTrack() {
        return redirectedSpotifyTrack;
    }

    public void setTitle(String title) {
        this.title.complete(title);
    }

    public void setId(String id) {
        this.id.complete(id);
    }

    public void setDuration(long duration) {
        this.duration.complete(duration);
    }

    private <E> E getCompleted(CompletableFuture<E> future) {
        try {
            return future.get(3, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException("YouTubeVideo loading timed out", e);
        }
    }

}
