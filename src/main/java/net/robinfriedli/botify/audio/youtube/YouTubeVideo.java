package net.robinfriedli.botify.audio.youtube;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Video;
import org.hibernate.Session;

/**
 * Interface for all classes that may represent a YouTube video. Currently implemented by {@link YouTubeVideoImpl}
 * as the standard implementation for YouTube videos and {@link HollowYouTubeVideo} for YouTube videos that are loaded
 * asynchronously. This interface extends {@link Playable}, meaning it can be added to the queued and be played directly.
 */
public interface YouTubeVideo extends Playable {

    /**
     * @return the title of the YouTube video, or in case of a cancelled {@link HollowYouTubeVideo} throw an
     * {@link InterruptedException} to signal that loading the tracks has been interrupted, the checked method
     * {@link Playable#getDisplayInterruptible()} will then show the video as "[UNAVAILABLE]"
     */
    String getTitle() throws InterruptedException;

    /**
     * like {@link #getTitle()} but throws a {@link TimeoutException} after the specified time limit if the
     * {@link HollowYouTubeVideo} is not loaded in time, the method {@link Playable#getDisplayInterruptible(long, TimeUnit)}
     * will then show the video as "Loading..."
     */
    String getTitle(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException;

    @Override
    default String getDisplay() throws InterruptedException {
        return getTitle();
    }

    @Override
    default String getDisplay(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException {
        return getTitle(timeOut, unit);
    }

    /**
     * @return the id of the YouTube video, throwing an {@link InterruptedException} if cancelled,
     * see {@link #getTitle()}
     */
    String getVideoId() throws InterruptedException;

    /**
     * @return the id of the YouTube video, throwing a {@link TimeoutException} if loading takes longer than the provided
     * amount of time, see {@link #getTitle(long, TimeUnit)}
     */
    String getVideoId(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException;

    @Override
    default String getId() throws InterruptedException {
        return getVideoId();
    }

    @Override
    default String getPlaybackUrl() throws InterruptedException {
        return String.format("https://www.youtube.com/watch?v=%s", getVideoId());
    }

    /**
     * @return the duration of the YouTube video in milliseconds or throw an {@link InterruptedException} if cancelled,
     * see {@link #getTitle()}
     */
    long getDuration() throws InterruptedException;

    /**
     * @return the duration of the YouTube video in milliseconds or throw a {@link TimeoutException} if loading takes
     * longer that the provided amount of time, see {@link #getTitle(long, TimeUnit)}
     */
    long getDuration(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException;

    @Override
    default long getDurationMs() throws InterruptedException {
        return getDuration();
    }

    @Override
    default long getDurationMs(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException {
        return getDuration(timeOut, unit);
    }

    @Override
    default PlaylistItem export(Playlist playlist, User user, Session session) {
        return new Video(this, user, playlist);
    }

    @Override
    default String getSource() {
        return "YouTube";
    }

    /**
     * @return if this YouTube video is the result of a redirected Spotify track, return the corresponding track,
     * else return null. For more about Spotify track redirection, see {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}
     */
    @Nullable
    Track getRedirectedSpotifyTrack();
}
