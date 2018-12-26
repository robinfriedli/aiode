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
    String getTitle();

    /**
     * @return the id of the YouTube video
     */
    String getId();

    /**
     * @return the duration of the YouTube video in milliseconds
     */
    long getDuration();

    /**
     * @return if this YouTube video is the result of a redirected Spotify track, return the corresponding track,
     * else return null. For more about Spotify track redirection, see {@link YouTubeService#redirectSpotify(Track)}
     */
    @Nullable
    Track getRedirectedSpotifyTrack();
}
