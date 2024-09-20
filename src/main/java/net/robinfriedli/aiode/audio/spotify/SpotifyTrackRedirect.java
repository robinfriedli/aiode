package net.robinfriedli.aiode.audio.spotify;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AbstractSoftCachedPlayable;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.UrlPlayable;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.concurrent.EagerFetchQueue;
import net.robinfriedli.aiode.entities.Episode;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.Song;
import net.robinfriedli.aiode.exceptions.CommandRuntimeException;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper;
import net.robinfriedli.aiode.function.CheckedConsumer;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import org.hibernate.Session;
import org.jetbrains.annotations.Nullable;

public class SpotifyTrackRedirect extends AbstractSoftCachedPlayable {

    private final SpotifyTrack spotifyTrack;

    private final CompletableFuture<FilebrokerPlayableWrapper> filebrokerPost;
    private final CompletableFuture<UrlPlayable> soundCloudTrack;
    private final HollowYouTubeVideo youTubeVideo;

    private volatile boolean loading = false;
    private volatile boolean canceled = false;

    public SpotifyTrackRedirect(SpotifyTrack spotifyTrack, YouTubeService youTubeService) {
        this.spotifyTrack = spotifyTrack;
        filebrokerPost = new CompletableFuture<>();
        soundCloudTrack = new CompletableFuture<>();
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

    public boolean isYouTube() {
        try {
            return applyToEither(playable -> playable instanceof YouTubeVideo);
        } catch (UnavailableResourceException e) {
            throw new CommandRuntimeException(e);
        }
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
     * Sets the redirected filebroker post or soundcloud track to the provided one or cancels it if null, the redirected YouTube video is expected
     * to be loaded by completing the {@link HollowYouTubeVideo} if there is no filebroker post or soundcloud track present.
     *
     * @param playable the filebroker post or soundcloud track the spotify track redirects to, will be cancelled if null
     */
    public void complete(@Nullable Playable playable) {
        switch (playable) {
            case FilebrokerPlayableWrapper filebrokerPost -> {
                this.filebrokerPost.complete(filebrokerPost);
                this.soundCloudTrack.cancel(true);
            }
            case UrlPlayable urlPlayable -> {
                this.filebrokerPost.cancel(true);
                this.soundCloudTrack.complete(urlPlayable);
            }
            case null -> {
                this.filebrokerPost.cancel(true);
                this.soundCloudTrack.cancel(true);
            }
            default -> throw new IllegalArgumentException("Unsupported playable: " + playable);
        }

        loading = false;
        notifyAll();
    }

    public void markLoading() {
        loading = true;
    }

    private <T> T getCompletedFuture(CompletableFuture<T> future) {
        try {
            try {
                return future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                fetch();

                return future.get(3, TimeUnit.MINUTES);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Loading timed out", e);
        } catch (CancellationException e) {
            return null;
        }
    }

    public FilebrokerPlayableWrapper getCompletedFilebrokerPost() {
        return getCompletedFuture(filebrokerPost);
    }

    public UrlPlayable getCompletedSoundCloudTrack() {
        return getCompletedFuture(soundCloudTrack);
    }

    @FunctionalInterface
    interface ResourceLoadingFunction<T, R> {
        R apply(T value) throws UnavailableResourceException;
    }

    private <T> T applyToEither(ResourceLoadingFunction<Playable, T> function) throws UnavailableResourceException {
        FilebrokerPlayableWrapper filebrokerPost = getCompletedFilebrokerPost();
        if (filebrokerPost != null) {
            return function.apply(filebrokerPost);
        }
        if (youTubeVideo.isDone()) {
            return function.apply(youTubeVideo);
        }
        UrlPlayable soundCloudTrack = getCompletedSoundCloudTrack();
        return function.apply(Objects.requireNonNullElse(soundCloudTrack, youTubeVideo));
    }

    private <T> T applyToEitherWithTimeout(long timeOut, TimeUnit unit, ResourceLoadingFunction<Playable, T> function) throws TimeoutException, UnavailableResourceException {
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

    private <T> T applyToEitherNow(T altValue, ResourceLoadingFunction<Playable, T> function) throws UnavailableResourceException {
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
        String name = spotifyTrack.getName();
        if (!Strings.isNullOrEmpty(name)) {
            return name;
        } else {
            return applyToEither(Playable::getTitle);
        }
    }

    @Override
    public String getTitle(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        if (!Strings.isNullOrEmpty(spotifyTrack.getName())) {
            return spotifyTrack.getName();
        }
        return applyToEitherWithTimeout(timeOut, unit, Playable::getTitle);
    }

    @Override
    public String getTitleNow(String alternativeValue) throws UnavailableResourceException {
        if (!Strings.isNullOrEmpty(spotifyTrack.getName())) {
            return spotifyTrack.getName();
        }
        return applyToEitherNow(alternativeValue, Playable::getTitle);
    }

    @Override
    public String getDisplay() throws UnavailableResourceException {
        String display = spotifyTrack.getDisplay();
        if (!Strings.isNullOrEmpty(display)) {
            return display;
        }
        return applyToEither(Playable::getDisplay);
    }

    @Override
    public String getDisplay(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        String display = spotifyTrack.getDisplay();
        if (!Strings.isNullOrEmpty(display)) {
            return display;
        }
        return applyToEitherWithTimeout(timeOut, unit, Playable::getDisplay);
    }

    @Override
    public String getDisplayNow(String alternativeValue) throws UnavailableResourceException {
        String display = spotifyTrack.getDisplay();
        if (!Strings.isNullOrEmpty(display)) {
            return display;
        }
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
        String albumCoverUrl = spotifyTrack.getAlbumCoverUrl();
        if (!Strings.isNullOrEmpty(albumCoverUrl)) {
            return albumCoverUrl;
        }
        try {
            return applyToEither(Playable::getAlbumCoverUrl);
        } catch (UnavailableResourceException e) {
            throw new CommandRuntimeException(e);
        }
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
