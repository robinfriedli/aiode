package net.robinfriedli.aiode.audio.youtube;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import net.robinfriedli.aiode.audio.AbstractSoftCachedPlayable;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;

/**
 * Represents a fully loaded YouTube video
 */
public class YouTubeVideoImpl extends AbstractSoftCachedPlayable implements YouTubeVideo {

    private final String title;
    private final String id;
    private final long duration;
    private SpotifyTrack redirectedSpotifyTrack;

    public YouTubeVideoImpl(String title, String id, long duration) {
        this.title = title;
        this.id = id;
        this.duration = duration;
    }

    @Override
    public String getDisplay() {
        return title;
    }

    @Override
    public String getDisplay(long timeOut, TimeUnit unit) {
        return getDisplay();
    }

    @Override
    public String getDisplayNow(String alternativeValue) {
        return getDisplay();
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
    public SpotifyTrack getRedirectedSpotifyTrack() {
        return redirectedSpotifyTrack;
    }

    @Override
    public void setRedirectedSpotifyTrack(@Nullable SpotifyTrack track) {
        redirectedSpotifyTrack = track;
    }
}
