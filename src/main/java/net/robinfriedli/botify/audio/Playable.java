package net.robinfriedli.botify.audio;

import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.robinfriedli.stringlist.StringListImpl;

/**
 * Wrapper class for everything that can be added to the {@link AudioQueue}
 */
public class Playable {

    private final AudioManager audioManager;
    private final Object delegate;

    public Playable(AudioManager audioManager, Object delegate) {
        this.audioManager = audioManager;
        this.delegate = delegate;
    }

    public Object delegate() {
        return delegate;
    }

    public String getPlaybackUrl() {
        if (delegate() instanceof Track) {
            return ((Track) delegate()).getPreviewUrl();
        } else if (delegate() instanceof YouTubeVideo) {
            return String.format("https://www.youtube.com/watch?v=%s", ((YouTubeVideo) delegate()).getId());
        } else {
            throw new UnsupportedOperationException("Unsupported playable: " + delegate().getClass().getSimpleName());
        }
    }

    public String getDisplay() {
        if (delegate() instanceof Track) {
            Track track = (Track) delegate();
            String name = track.getName();
            String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
            return String.format("%s by %s", name, artistString);
        } else if (delegate() instanceof YouTubeVideo) {
            return ((YouTubeVideo) delegate()).getTitle();
        } else {
            throw new UnsupportedOperationException("Unsupported playable: " + delegate().getClass().getSimpleName());
        }
    }

    public long getDurationMs() {
        if (delegate() instanceof Track) {
            return ((Track) delegate()).getDurationMs();
        } else if (delegate() instanceof YouTubeVideo) {
            return ((YouTubeVideo) delegate()).getDuration();
        } else {
            throw new UnsupportedOperationException("Unsupported playable: " + delegate().getClass().getSimpleName());
        }
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }
}
