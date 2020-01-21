package net.robinfriedli.botify.audio.youtube;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AbstractSoftCachedPlayable;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.spotify.SpotifyRedirectService;
import net.robinfriedli.botify.concurrent.EagerFetchQueue;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import net.robinfriedli.botify.function.CheckedConsumer;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import org.hibernate.Session;

/**
 * YouTube video when the data has not been loaded yet. This is used for YouTube playlist elements or Spotify tracks that
 * need to be redirected.
 */
public class HollowYouTubeVideo extends AbstractSoftCachedPlayable implements YouTubeVideo {

    private final CompletableFuture<String> title;
    private final CompletableFuture<String> id;
    private final CompletableFuture<Long> duration;
    private final YouTubeService youTubeService;
    @Nullable
    private Track redirectedSpotifyTrack;
    private boolean canceled = false;
    private volatile boolean isHollow = true;

    public HollowYouTubeVideo(YouTubeService youTubeService) {
        this(youTubeService, null);
    }

    public HollowYouTubeVideo(YouTubeService youTubeService, @Nullable Track redirectedSpotifyTrack) {
        this.title = new CompletableFuture<>();
        this.id = new CompletableFuture<>();
        this.duration = new CompletableFuture<>();
        this.youTubeService = youTubeService;
        this.redirectedSpotifyTrack = redirectedSpotifyTrack;
    }

    @Override
    public String getDisplay() throws UnavailableResourceException {
        return getCompleted(title);
    }

    public void setTitle(String title) {
        isHollow = false;
        this.title.complete(title);
    }

    @Override
    public String getDisplay(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return getWithTimeout(title, timeOut, unit);
    }

    @Override
    public String getDisplayNow(String alternativeValue) throws UnavailableResourceException {
        return getNow(title, alternativeValue);
    }

    @Override
    public String getVideoId() throws UnavailableResourceException {
        return getCompleted(id);
    }

    public void setId(String id) {
        isHollow = false;
        this.id.complete(id);
    }

    @Override
    public long getDuration() throws UnavailableResourceException {
        return getCompleted(duration);
    }

    public void setDuration(long duration) {
        isHollow = false;
        this.duration.complete(duration);
    }

    @Override
    public long getDuration(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return getWithTimeout(duration, timeOut, unit);
    }

    @Override
    public long getDurationNow(long alternativeValue) throws UnavailableResourceException {
        return getNow(duration, alternativeValue);
    }

    @Nullable
    @Override
    public Track getRedirectedSpotifyTrack() {
        return redirectedSpotifyTrack;
    }

    @Override
    public void setRedirectedSpotifyTrack(@Nullable Track track) {
        redirectedSpotifyTrack = track;
    }

    public void cancel() {
        canceled = true;
        title.cancel(false);
        id.cancel(false);
        duration.cancel(false);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void awaitCompletion() {
        try {
            title.get(5, TimeUnit.MINUTES);
            id.get(5, TimeUnit.MINUTES);
            duration.get(5, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new RuntimeException("Waiting for video timed out", e);
        } catch (InterruptedException | ExecutionException | CancellationException ignored) {
        }
    }

    public boolean isHollow() {
        return isHollow;
    }

    public boolean isDone() {
        return title.isDone() && id.isDone() && duration.isDone();
    }

    public void markLoading() {
        isHollow = false;
    }

    @Override
    public Playable fetch() {
        // only supported for Spotify redirect as YouTube does not allow loading specific playlist items
        if (isHollow() && redirectedSpotifyTrack != null) {
            markLoading();
            EagerFetchQueue.submitFetch(() -> StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                SpotifyRedirectService spotifyRedirectService = new SpotifyRedirectService(session, youTubeService);
                spotifyRedirectService.redirectTrack(this);
            }));
        }
        return this;
    }

    private <E> E getCompleted(CompletableFuture<E> future) throws UnavailableResourceException {
        try {
            try {
                return future.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fetch();

                return future.get(3, TimeUnit.MINUTES);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Video loading timed out", e);
        } catch (CancellationException e) {
            throw new UnavailableResourceException();
        }
    }

    private <E> E getWithTimeout(CompletableFuture<E> future, long time, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        try {
            return future.get(time, unit);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (CancellationException e) {
            throw new UnavailableResourceException();
        }
    }

    private <E> E getNow(CompletableFuture<E> future, E alternativeValue) throws UnavailableResourceException {
        try {
            return future.getNow(alternativeValue);
        } catch (CompletionException e) {
            throw new RuntimeException(e);
        } catch (CancellationException e) {
            throw new UnavailableResourceException();
        }
    }

}
