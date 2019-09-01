package net.robinfriedli.botify.audio.youtube;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import org.hibernate.Session;

/**
 * Interface for all classes that may represent a YouTube video. Currently implemented by {@link YouTubeVideoImpl}
 * as the standard implementation for YouTube videos and {@link HollowYouTubeVideo} for YouTube videos that are loaded
 * asynchronously. This interface extends {@link Playable}, meaning it can be added to the queued and be played directly.
 */
public interface YouTubeVideo extends Playable {

    /**
     * @return the title of the YouTube video, or in case of a cancelled {@link HollowYouTubeVideo} throw an
     * {@link UnavailableResourceException} to signal that loading the tracks has been cancelled, the checked method
     * {@link Playable#display()} will then show the video as "[UNAVAILABLE]"
     */
    String getTitle() throws UnavailableResourceException;

    /**
     * like {@link #getTitle()} but throws a {@link TimeoutException} after the specified time limit if the
     * {@link HollowYouTubeVideo} is not loaded in time, the method {@link Playable#display(long, TimeUnit)}
     * will then show the video as "Loading..."
     */
    String getTitle(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException;

    @Override
    default String getDisplay() throws UnavailableResourceException {
        return getTitle();
    }

    @Override
    default String getDisplay(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return getTitle(timeOut, unit);
    }

    @Override
    default String getDisplayNow(String alternativeValue) throws UnavailableResourceException {
        return getDisplay();
    }

    /**
     * @return the id of the YouTube video, throwing an {@link UnavailableResourceException} if cancelled,
     * see {@link #getTitle()}
     */
    String getVideoId() throws UnavailableResourceException;

    @Override
    default String getId() throws UnavailableResourceException {
        return getRedirectedSpotifyTrack() != null ? getRedirectedSpotifyTrack().getId() : getVideoId();
    }

    @Override
    default String getPlaybackUrl() throws UnavailableResourceException {
        return String.format("https://www.youtube.com/watch?v=%s", getVideoId());
    }

    /**
     * @return the duration of the YouTube video in milliseconds or throw an {@link UnavailableResourceException} if cancelled,
     * see {@link #getTitle()}
     */
    long getDuration() throws UnavailableResourceException;

    /**
     * @return the duration of the YouTube video in milliseconds or throw a {@link TimeoutException} if loading takes
     * longer that the provided amount of time, see {@link #getTitle(long, TimeUnit)}
     */
    long getDuration(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException;

    @Override
    default long getDurationMs() throws UnavailableResourceException {
        return getDuration();
    }

    @Override
    default long getDurationMs(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return getDuration(timeOut, unit);
    }

    @Override
    default long getDurationNow(long alternativeValue) throws UnavailableResourceException {
        return getDuration();
    }

    @Override
    default PlaylistItem export(Playlist playlist, User user, Session session) {
        return new Video(this, user, playlist);
    }

    @Override
    default String getSource() {
        return getRedirectedSpotifyTrack() != null ? "Spotify" : "YouTube";
    }

    /**
     * @return if this YouTube video is the result of a redirected Spotify track, return the corresponding track,
     * else return null. For more about Spotify track redirection, see {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}
     */
    @Nullable
    Track getRedirectedSpotifyTrack();

    void setRedirectedSpotifyTrack(@Nullable Track track);
}
