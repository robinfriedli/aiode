package net.robinfriedli.botify.audio;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import org.hibernate.Session;

/**
 * interface for any class that Botify accepts as track that can be added to the queue
 */
public interface Playable {

    /**
     * @return The url of the music file to stream
     * @throws UnavailableResourceException if loading the item was cancelled due to being unavailable or cancelled
     */
    String getPlaybackUrl() throws UnavailableResourceException;

    /**
     * @return an id that uniquely identifies this playable together with {@link #getSource()}
     */
    String getId() throws UnavailableResourceException;

    /**
     * @return The title of the Playable. For Spotify it's the track name, for YouTube it's the video title and for other
     * URLs it's either the tile or the URL depending on whether or not a title could be found
     * @throws UnavailableResourceException if loading the item was cancelled due to being unavailable or cancelled
     */
    String getDisplay() throws UnavailableResourceException;

    /**
     * @return the display of the Playable, showing cancelled Playables as "[UNAVAILABLE]"
     */
    default String display() {
        try {
            return getDisplay();
        } catch (UnavailableResourceException e) {
            return "[UNAVAILABLE]";
        }
    }

    /**
     * @return the display of the Playable
     * @throws UnavailableResourceException if loading the item was cancelled due to being unavailable or cancelled
     * @throws TimeoutException             if the data is not loaded in time
     */
    String getDisplay(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException;

    /**
     * @return the display of the Playable, showing cancelled Playables as "[UNAVAILABLE]" and Playables that couldn't
     * load in time as "Loading..."
     */
    default String display(long timeOut, TimeUnit unit) {
        try {
            return getDisplay(timeOut, unit);
        } catch (UnavailableResourceException e) {
            return "[UNAVAILABLE]";
        } catch (TimeoutException e) {
            return "Loading...";
        }
    }

    /**
     * Return the display of the playable getting the current value without waiting for completion of the value,
     * returning the provided alternativeValue instead if the playable is not completed.
     *
     * @param alternativeValue the value to return instead if the value has not been loaded yet
     * @return the display
     * @throws UnavailableResourceException if loading the item was cancelled due to being unavailable or cancelled
     */
    String getDisplayNow(String alternativeValue) throws UnavailableResourceException;

    default String getDisplayNow() {
        try {
            return getDisplayNow("Loading...");
        } catch (UnavailableResourceException e) {
            return "[UNAVAILABLE]";
        }
    }

    /**
     * @return The duration of the audio track in milliseconds
     * @throws UnavailableResourceException if loading the item was cancelled due to being unavailable or cancelled
     */
    long getDurationMs() throws UnavailableResourceException;

    /**
     * @return The duration of the audio track in milliseconds or 0 if loading the Playable was cancelled
     */
    default long durationMs() {
        try {
            return getDurationMs();
        } catch (UnavailableResourceException e) {
            return 0;
        }
    }

    /**
     * @return The duration of the audio track in milliseconds or 0 if loading the Playable was cancelled or did
     * not load in time
     */
    long getDurationMs(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException;

    default long durationMs(long timeOut, TimeUnit unit) {
        try {
            return getDurationMs(timeOut, unit);
        } catch (UnavailableResourceException | TimeoutException e) {
            return 0;
        }
    }

    /**
     * Get the duration now without waiting for the playable to finish, returning the alternativeValue instead if not
     * done
     *
     * @param alternativeValue the value to return if the value has not been loaded yet
     * @return the display
     * @throws UnavailableResourceException if loading the item was cancelled due to being unavailable or cancelled
     */
    long getDurationNow(long alternativeValue) throws UnavailableResourceException;

    default long getDurationNow() {
        try {
            return getDurationNow(0);
        } catch (UnavailableResourceException e) {
            return 0;
        }
    }

    @Nullable
    String getAlbumCoverUrl();

    /**
     * Exports this playable as a persistable {@link PlaylistItem}
     *
     * @param playlist the playlist this item will be a part of
     * @param user     the user that added the item
     * @return the create item (not persisted yet)
     */
    PlaylistItem export(Playlist playlist, User user, Session session);

    /**
     * @return the name of the source of the Playable. Spotify, YouTube or URL.
     */
    String getSource();

    /**
     * @return a cached instance of the AudioTrack that was the result of loading this Playable. Note that the same
     * AudioTrack instance cannot be played multiple times and a you should create a clone using {@link AudioTrack#makeClone()} instead.
     */
    @Nullable
    AudioTrack getCached();

    /**
     * Cache the resulting AudioTrack when playing this Playable
     *
     * @param audioTrack the resulting AudioTrack
     */
    void setCached(AudioTrack audioTrack);

}
