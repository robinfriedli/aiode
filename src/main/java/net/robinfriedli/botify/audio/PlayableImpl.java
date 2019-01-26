package net.robinfriedli.botify.audio;

import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.stringlist.StringListImpl;

/**
 * A standard Playable that can either be a YouTube video or Spotify track
 */
public class PlayableImpl implements Playable {

    private final Object delegate;

    public PlayableImpl(Object delegate) {
        this.delegate = delegate;
    }

    public Object delegate() {
        return delegate;
    }

    @Override
    public String getPlaybackUrl() throws InterruptedException {
        if (delegate() instanceof Track) {
            return ((Track) delegate()).getPreviewUrl();
        } else if (delegate() instanceof YouTubeVideo) {
            return String.format("https://www.youtube.com/watch?v=%s", ((YouTubeVideo) delegate()).getId());
        } else {
            throw new UnsupportedOperationException("Unsupported playable: " + delegate().getClass().getSimpleName());
        }
    }

    @Override
    public String getDisplay() throws InterruptedException {
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

    @Override
    public long getDurationMs() throws InterruptedException {
        if (delegate() instanceof Track) {
            return ((Track) delegate()).getDurationMs();
        } else if (delegate() instanceof YouTubeVideo) {
            return ((YouTubeVideo) delegate()).getDuration();
        } else {
            throw new UnsupportedOperationException("Unsupported playable: " + delegate().getClass().getSimpleName());
        }
    }

    @Override
    public XmlElement export(Context context, User user) {
        if (delegate() instanceof Track) {
            return new Song((Track) delegate(), user, context);
        } else if (delegate() instanceof YouTubeVideo) {
            return new Video((YouTubeVideo) delegate(), user, context);
        } else {
            throw new UnsupportedOperationException("Unsupported playable: " + delegate().getClass().getSimpleName());
        }
    }
}
