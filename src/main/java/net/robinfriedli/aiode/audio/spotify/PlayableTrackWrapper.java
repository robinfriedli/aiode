package net.robinfriedli.aiode.audio.spotify;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.audio.AbstractSoftCachedPlayable;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.entities.Episode;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.Song;
import org.hibernate.Session;

/**
 * Playable wrapper for tracks are played directly from Spotify. Currently this is only possible if you play the preview
 * mp3 provided by Spotify using the $preview argument. Normally Spotify tracks are wrapped by {@link HollowYouTubeVideo}
 * and, usually asynchronously, redirected to YouTube, see {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}
 */
public class PlayableTrackWrapper extends AbstractSoftCachedPlayable implements Playable {

    private final SpotifyTrack trackWrapper;

    public PlayableTrackWrapper(SpotifyTrack track) {
        this.trackWrapper = track;
    }

    @Override
    public String getPlaybackUrl() {
        return trackWrapper.getPreviewUrl();
    }

    @Override
    public String getId() {
        return trackWrapper.getId();
    }

    @Override
    public String getTitle() {
        return trackWrapper.getName();
    }

    @Override
    public String getTitle(long timeOut, TimeUnit unit) {
        return getTitle();
    }

    @Override
    public String getTitleNow(String alternativeValue) {
        return getTitle();
    }

    @Override
    public String getDisplay() {
        return trackWrapper.getDisplay();
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
    public long getDurationMs() {
        return trackWrapper.getDurationMs();
    }

    @Override
    public long getDurationMs(long timeOut, TimeUnit unit) {
        return getDurationMs();
    }

    @Override
    public long getDurationNow(long alternativeValue) {
        return getDurationMs();
    }

    @Nullable
    @Override
    public String getAlbumCoverUrl() {
        return trackWrapper.getAlbumCoverUrl();
    }

    @Override
    public PlaylistItem export(Playlist playlist, User user, Session session) {
        return trackWrapper.exhaustiveMatch(
            track -> new Song(track, user, playlist, session),
            episode -> new Episode(episode, user, playlist)
        );
    }

    @Override
    public Source getSource() {
        return Source.SPOTIFY;
    }

    public SpotifyTrack getTrack() {
        return trackWrapper;
    }

    public SpotifyTrackKind getKind() {
        return trackWrapper.getKind();
    }

}
