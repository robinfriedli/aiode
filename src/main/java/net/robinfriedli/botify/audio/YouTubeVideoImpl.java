package net.robinfriedli.botify.audio;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;

/**
 * Bean for YouTube videos
 */
public class YouTubeVideoImpl implements YouTubeVideo {

    private final String title;
    private final String id;
    private final long duration;
    @Nullable
    private final Track redirectedSpotifyTrack;

    public YouTubeVideoImpl(String title, String id, long duration) {
        this.title = title;
        this.id = id;
        this.duration = duration;
        redirectedSpotifyTrack = null;
    }

    public YouTubeVideoImpl(String title, String id, long duration, @Nullable Track redirectedSpotifyTrack) {
        this.title = title;
        this.id = id;
        this.duration = duration;
        this.redirectedSpotifyTrack = redirectedSpotifyTrack;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    @Nullable
    public Track getRedirectedSpotifyTrack() {
        return redirectedSpotifyTrack;
    }
}
