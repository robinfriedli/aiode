package net.robinfriedli.botify.audio.youtube;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;

/**
 * Bean for YouTube videos
 */
public class YouTubeVideoImpl implements YouTubeVideo {

    private final String title;
    private final String id;
    private final long duration;

    public YouTubeVideoImpl(String title, String id, long duration) {
        this.title = title;
        this.id = id;
        this.duration = duration;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getTitle(long timeOut, TimeUnit unit) {
        return getTitle();
    }

    @Override
    public String getVideoId() {
        return id;
    }

    @Override
    public String getVideoId(long timeOut, TimeUnit unit) {
        return getVideoId();
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public long getDuration(long timeOut, TimeUnit unit) {
        return getDuration();
    }

    @Override
    @Nullable
    public Track getRedirectedSpotifyTrack() {
        return null;
    }
}
