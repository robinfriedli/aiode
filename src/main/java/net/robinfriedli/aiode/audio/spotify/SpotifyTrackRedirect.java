package net.robinfriedli.aiode.audio.spotify;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AbstractSoftCachedPlayable;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.concurrent.EagerFetchQueue;
import net.robinfriedli.aiode.entities.Episode;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.Song;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper;
import net.robinfriedli.aiode.function.CheckedConsumer;
import net.robinfriedli.aiode.function.CheckedFunction;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import org.hibernate.Session;
import org.jetbrains.annotations.Nullable;

public class SpotifyTrackRedirect extends AbstractSoftCachedPlayable {

    private final SpotifyTrack spotifyTrack;

    private final CompletableFuture<FilebrokerPlayableWrapper> filebrokerPost;
    private final HollowYouTubeVideo youTubeVideo;

    private volatile boolean loading = false;
    private volatile boolean canceled = false;

    public SpotifyTrackRedirect(SpotifyTrack spotifyTrack, YouTubeService youTubeService) {
        this.spotifyTrack = spotifyTrack;
        filebrokerPost = new CompletableFuture<>();
        youTubeVideo = new HollowYouTubeVideo(youTubeService, spotifyTrack);
    }

    public SpotifyTrack getSpotifyTrack() {
        return spotifyTrack;
    }

    public CompletableFuture<FilebrokerPlayableWrapper> getFilebrokerPost() {
        return filebrokerPost;
    }

    public HollowYouTubeVideo getYouTubeVideo() {
        return youTubeVideo;
    }

    public boolean isDone() {
        return (filebrokerPost.isDone() && !filebrokerPost.isCancelled() && !filebrokerPost.isCompletedExceptionally()) || youTubeVideo.isDone();
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void cancel() {
        canceled = true;
        youTubeVideo.cancel();
    }

    public boolean isRedirectedToFilebroker() {
        return filebrokerPost.isDone() && !filebrokerPost.isCancelled() && !filebrokerPost.isCompletedExceptionally();
    }

    /**
     * Complete the redirect and notify threads awaiting its completion, current thread must hold this object's monitor.
     * Sets the redirected filebroker post to the provided one or cancels it if null, the redirected YouTube video is expected
     * to be loaded by completing the {@link HollowYouTubeVideo} if there is no filebroker post present.
     *
     * @param filebrokerPost the filebroker post the spotify track redirects to, will be cancelled if null
     */
    public void complete(@Nullable FilebrokerPlayableWrapper filebrokerPost) {
        if (filebrokerPost != null) {
            this.filebrokerPost.complete(filebrokerPost);
        } else {
            this.filebrokerPost.cancel(true);
        }

        loading = false;
        notifyAll();
    }

    public void markLoading() {
        loading = true;
    }

    private FilebrokerPlayableWrapper getCompletedFilebrokerPost() {
        try {
            try {
                return filebrokerPost.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                fetch();

                return filebrokerPost.get(3, TimeUnit.MINUTES);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Filebroker loading timed out", e);
        } catch (CancellationException e) {
            return null;
        }
    }

    private <T> T applyToEither(CheckedFunction<Playable, T> function) {
        FilebrokerPlayableWrapper filebrokerPost = getCompletedFilebrokerPost();
        return function.apply(Objects.requireNonNullElse(filebrokerPost, youTubeVideo));
    }

    private <T> T applyToEitherWithTimeout(long timeOut, TimeUnit unit, CheckedFunction<Playable, T> function) throws TimeoutException {
        if (isDone()) {
            return applyToEither(function);
        }

        synchronized (this) {
            if (isDone()) {
                return applyToEither(function);
            }

            try {
                wait(unit.toMillis(timeOut));
                if (isDone()) {
                    return applyToEither(function);
                } else {
                    throw new TimeoutException();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private <T> T applyToEitherNow(T altValue, CheckedFunction<Playable, T> function) {
        if (isDone()) {
            return applyToEither(function);
        } else {
            return altValue;
        }
    }

    @Override
    public String getPlaybackUrl() throws UnavailableResourceException {
        return applyToEither(Playable::getPlaybackUrl);
    }

    @Override
    public String getId() throws UnavailableResourceException {
        return spotifyTrack.getId();
    }

    @Override
    public String getTitle() throws UnavailableResourceException {
        return applyToEither(Playable::getTitle);
    }

    @Override
    public String getTitle(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return applyToEitherWithTimeout(timeOut, unit, Playable::getTitle);
    }

    @Override
    public String getTitleNow(String alternativeValue) {
        return applyToEitherNow(alternativeValue, Playable::getTitle);
    }

    @Override
    public String getDisplay() throws UnavailableResourceException {
        return applyToEither(Playable::getDisplay);
    }

    @Override
    public String getDisplay(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return applyToEitherWithTimeout(timeOut, unit, Playable::getDisplay);
    }

    @Override
    public String getDisplayNow(String alternativeValue) throws UnavailableResourceException {
        return applyToEitherNow(alternativeValue, Playable::getDisplay);
    }

    @Override
    public long getDurationMs() throws UnavailableResourceException {
        return applyToEither(Playable::getDurationMs);
    }

    @Override
    public long getDurationMs(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return applyToEitherWithTimeout(timeOut, unit, Playable::getDurationMs);
    }

    @Override
    public long getDurationNow(long alternativeValue) throws UnavailableResourceException {
        return applyToEitherNow(alternativeValue, Playable::getDurationMs);
    }

    @Override
    public @Nullable String getAlbumCoverUrl() {
        return applyToEither(Playable::getAlbumCoverUrl);
    }

    @Override
    public PlaylistItem export(Playlist playlist, User user, Session session) {
        return spotifyTrack.exhaustiveMatch(
            track -> new Song(track, user, playlist, session),
            episode -> new Episode(episode, user, playlist)
        );
    }

    @Override
    public Source getSource() {
        return Source.SPOTIFY;
    }

    @Override
    public Playable fetch() {
        if (!isDone() && !loading) {
            synchronized (this) {
                if (isDone() || loading) {
                    return this;
                }

                markLoading();
                EagerFetchQueue.submitFetch(() -> StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                    SpotifyRedirectService spotifyRedirectService = new SpotifyRedirectService(Aiode.get().getFilebrokerApi(), session, Aiode.get().getAudioManager().getYouTubeService());
                    spotifyRedirectService.redirectTrack(this);
                }));
            }
        }
        return this;
    }
}
