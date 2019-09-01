package net.robinfriedli.botify.audio.youtube;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.botify.audio.AbstractSoftCachedPlayable;

/**
 * Represents a fully loaded YouTube video
 */
public class YouTubeVideoImpl extends AbstractSoftCachedPlayable implements YouTubeVideo {

    private final String title;
    private final String id;
    private final long duration;
    private Track redirectedSpotifyTrack;

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
        return redirectedSpotifyTrack;
    }

    @Override
    public void setRedirectedSpotifyTrack(@Nullable Track track) {
        redirectedSpotifyTrack = track;
    }
}
