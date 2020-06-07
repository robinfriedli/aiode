package net.robinfriedli.botify.audio.youtube;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.spotify.SpotifyTrack;
import net.robinfriedli.botify.entities.Episode;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import org.hibernate.Session;

/**
 * Interface for all classes that may represent a YouTube video. Currently implemented by {@link YouTubeVideoImpl}
 * as the standard implementation for YouTube videos and {@link HollowYouTubeVideo} for YouTube videos that are loaded
 * asynchronously. This interface extends {@link Playable}, meaning it can be added to the queued and be played directly.
 */
public interface YouTubeVideo extends Playable {

    /**
     * @return the id of the YouTube video, throwing an {@link UnavailableResourceException} if cancelled
     */
    String getVideoId() throws UnavailableResourceException;

    @Override
    default String getId() throws UnavailableResourceException {
        return getRedirectedSpotifyTrack() != null ? getRedirectedSpotifyTrack().getId() : getVideoId();
    }

    @Override
    default String getTitle() throws UnavailableResourceException {
        return getRedirectedSpotifyTrack() != null ? getRedirectedSpotifyTrack().getName() : getDisplay();
    }

    @Override
    default String getTitle(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return getRedirectedSpotifyTrack() != null ? getRedirectedSpotifyTrack().getName() : getDisplay(timeOut, unit);
    }

    @Override
    default String getTitleNow(String alternativeValue) throws UnavailableResourceException {
        return getRedirectedSpotifyTrack() != null ? getRedirectedSpotifyTrack().getName() : getDisplayNow(alternativeValue);
    }

    @Override
    default String getPlaybackUrl() throws UnavailableResourceException {
        return String.format("https://www.youtube.com/watch?v=%s", getVideoId());
    }

    /**
     * @return the duration of the YouTube video in milliseconds or throw an {@link UnavailableResourceException} if cancelled
     */
    long getDuration() throws UnavailableResourceException;

    /**
     * @return the duration of the YouTube video in milliseconds or throw a {@link TimeoutException} if loading takes
     * longer that the provided amount of time
     */
    long getDuration(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException;

    @Override
    default long getDurationMs() throws UnavailableResourceException {
        return getDuration();
    }

    @Override
    default long getDurationMs(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException {
        return getDuration(timeOut, unit);
    }

    @Override
    default long getDurationNow(long alternativeValue) throws UnavailableResourceException {
        return getDuration();
    }

    @Nullable
    @Override
    default String getAlbumCoverUrl() {
        if (getRedirectedSpotifyTrack() != null) {
            getRedirectedSpotifyTrack().getAlbumCoverUrl();
        }

        return null;
    }

    @Override
    default PlaylistItem export(Playlist playlist, User user, Session session) {
        if (getRedirectedSpotifyTrack() != null) {
            return getRedirectedSpotifyTrack().exhaustiveMatch(
                track -> new Song(track, user, playlist, session),
                episode -> new Episode(episode, user, playlist)
            );
        }
        return new Video(this, user, playlist, session);
    }

    @Override
    default Source getSource() {
        return getRedirectedSpotifyTrack() != null ? Source.SPOTIFY : Source.YOUTUBE;
    }

    /**
     * @return if this YouTube video is the result of a redirected Spotify track, return the corresponding track,
     * else return null. For more about Spotify track redirection, see {@link YouTubeService#redirectSpotify(HollowYouTubeVideo)}
     */
    @Nullable
    SpotifyTrack getRedirectedSpotifyTrack();

    void setRedirectedSpotifyTrack(@Nullable SpotifyTrack track);
}
