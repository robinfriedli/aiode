package net.robinfriedli.botify.audio.spotify;

import java.util.concurrent.TimeUnit;

import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.AbstractSoftCachedPlayable;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

/**
 * Playable wrapper for tracks are played directly from Spotify. Currently this is only possible if you play the preview
 * mp3 provided by Spotify using the $preview argument. Normally Spotify tracks are wrapped by {@link HollowYouTubeVideo}
 * and, usually asynchronously, redirected to YouTube, see {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}
 */
public class TrackWrapper extends AbstractSoftCachedPlayable implements Playable {

    private final Track track;

    public TrackWrapper(Track track) {
        this.track = track;
    }

    @Override
    public String getPlaybackUrl() {
        return track.getPreviewUrl();
    }

    @Override
    public String getId() {
        return track.getId();
    }

    @Override
    public String getDisplay() {
        String name = track.getName();
        String artistString = StringListImpl.create(track.getArtists(), ArtistSimplified::getName).toSeparatedString(", ");
        return String.format("%s by %s", name, artistString);
    }

    @Override
    public String getDisplay(long timeOut, TimeUnit unit) {
        return getDisplay();
    }

    @Override
    public long getDurationMs() {
        return track.getDurationMs();
    }

    @Override
    public long getDurationMs(long timeOut, TimeUnit unit) {
        return getDurationMs();
    }

    @Override
    public PlaylistItem export(Playlist playlist, User user, Session session) {
        return new Song(track, user, playlist, session);
    }

    @Override
    public String getSource() {
        return "Spotify";
    }

    public Track getTrack() {
        return track;
    }
}
