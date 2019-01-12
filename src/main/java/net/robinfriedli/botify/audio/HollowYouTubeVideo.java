package net.robinfriedli.botify.audio;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;

/**
 * YouTube video when the data has not been loaded yet. This is used for YouTube playlist elements or Spotify tracks that
 * need to be redirected.
 */
public class HollowYouTubeVideo implements YouTubeVideo {

    private final YouTubeService youTubeService;
    private final CompletableFuture<String> title;
    private final CompletableFuture<String> id;
    private final CompletableFuture<Long> duration;
    @Nullable
    private final Track redirectedSpotifyTrack;
    private boolean canceled = false;

    public HollowYouTubeVideo(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
        this.title = new CompletableFuture<>();
        this.id = new CompletableFuture<>();
        this.duration = new CompletableFuture<>();
        this.redirectedSpotifyTrack = null;
    }

    public HollowYouTubeVideo(YouTubeService youTubeService, @Nullable Track redirectedSpotifyTrack) {
        this.youTubeService = youTubeService;
        this.title = new CompletableFuture<>();
        this.id = new CompletableFuture<>();
        this.duration = new CompletableFuture<>();
        this.redirectedSpotifyTrack = redirectedSpotifyTrack;
    }

    @Override
    public String getTitle() throws InterruptedException {
        return getCompleted(title);
    }

    @Override
    public String getId() throws InterruptedException {
        return getCompleted(id);
    }

    @Override
    public long getDuration() throws InterruptedException {
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

    public void cancel() {
        title.cancel(false);
        id.cancel(false);
        duration.cancel(false);
        canceled = true;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public boolean isHollow() {
        return !(title.isDone() || id.isDone() || duration.isDone());
    }

    private <E> E getCompleted(CompletableFuture<E> future) throws InterruptedException {
        try {
            if (!future.isDone() && redirectedSpotifyTrack != null) {
                youTubeService.redirectSpotify(this);
            }

            return future.get(3, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Video loading timed out", e);
        } catch (CancellationException e) {
            throw new InterruptedException();
        }
    }
}
