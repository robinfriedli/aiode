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
 * Represents a YouTube video
 */
public interface YouTubeVideo extends Playable {

    /**
     * @return the title of the YouTube video
     */
    String getTitle() throws InterruptedException;

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
     * @return the id of the YouTube video
     */
    String getId() throws InterruptedException;

    String getId(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException;

    @Override
    default String getPlaybackUrl() throws InterruptedException {
        return String.format("https://www.youtube.com/watch?v=%s", getId());
    }

    /**
     * @return the duration of the YouTube video in milliseconds
     */
    long getDuration() throws InterruptedException;

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
