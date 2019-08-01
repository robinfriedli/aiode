package net.robinfriedli.botify.audio;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import org.hibernate.Session;

/**
 * interface for any class that Botify accepts as track that can be added to the queue
 */
public interface Playable {

    /**
     * @return The url of the music file to stream
     * @throws InterruptedException if the thread loading the data asynchronously gets interrupted
     */
    String getPlaybackUrl() throws InterruptedException;

    /**
     * @return an id that uniquely identifies this playable together with {@link #getSource()}
     */
    String getId() throws InterruptedException;

    /**
     * @return The title of the Playable. For Spotify it's the track name, for YouTube it's the video title and for other
     * URLs it's either the tile or the URL depending on whether or not a title could be found
     * @throws InterruptedException if the thread loading the data asynchronously gets interrupted
     */
    String getDisplay() throws InterruptedException;

    /**
     * @return the display of the Playable, showing interrupted Playables as "[UNAVAILABLE]"
     */
    default String getDisplayInterruptible() {
        try {
            return getDisplay();
        } catch (InterruptedException e) {
            return "[UNAVAILABLE]";
        }
    }

    /**
     * @return the display of the Playable
     * @throws InterruptedException if the thread loading the data asynchronously gets interrupted
     * @throws TimeoutException if the data is not loaded in time
     */
    String getDisplay(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException;

    /**
     * @return the display of the Playable, showing interrupted Playables as "[UNAVAILABLE]" and Playables that couldn't
     * load in time as "Loading..."
     */
    default String getDisplayInterruptible(long timeOut, TimeUnit unit) {
        try {
            return getDisplay(timeOut, unit);
        } catch (InterruptedException e) {
            return "[UNAVAILABLE]";
        } catch (TimeoutException e) {
            return "Loading...";
        }
    }

    /**
     * @return The duration of the audio track in milliseconds
     * @throws InterruptedException if loading the data asynchronously was interrupted
     */
    long getDurationMs() throws InterruptedException;

    /**
     * @return The duration of the audio track in milliseconds or 0 if loading the Playable was interrupted
     */
    default long getDurationMsInterruptible() {
        try {
            return getDurationMs();
        } catch (InterruptedException e) {
            return 0;
        }
    }

    /**
     * @return The duration of the audio track in milliseconds or 0 if loading the Playable was interrupted or did
     * not load in time
     */
    long getDurationMs(long timeOut, TimeUnit unit) throws InterruptedException, TimeoutException;

    default long getDurationMsInterruptible(long timeOut, TimeUnit unit) {
        try {
            return getDurationMs(timeOut, unit);
        } catch (InterruptedException | TimeoutException e) {
            return 0;
        }
    }

    /**
     * Exports this playable as a persistable {@link PlaylistItem}
     *
     * @param playlist the playlist this item will be a part of
     * @param user the user that added the item
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

    default boolean matches(Playable playable) {
        if (playable == null) {
            return false;
        }

        try {
            return getSource().equals(playable.getSource()) && getId().equals(playable.getId());
        } catch (InterruptedException ignored) {
        }

        return false;
    }

}
