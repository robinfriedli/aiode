package net.robinfriedli.botify.audio;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;

/**
 * Represents a YouTube video
 */
public interface YouTubeVideo {

    /**
     * @return the title of the YouTube video
     */
    String getTitle() throws InterruptedException;

    /**
     * @return the id of the YouTube video
     */
    String getId() throws InterruptedException;

    /**
     * @return the duration of the YouTube video in milliseconds
     */
    long getDuration() throws InterruptedException;

    /**
     * @return if this YouTube video is the result of a redirected Spotify track, return the corresponding track,
     * else return null. For more about Spotify track redirection, see {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}
     */
    @Nullable
    Track getRedirectedSpotifyTrack();
}
