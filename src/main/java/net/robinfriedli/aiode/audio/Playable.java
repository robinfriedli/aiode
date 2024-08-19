package net.robinfriedli.aiode.audio;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import org.hibernate.Session;

/**
 * interface for any class that Aiode accepts as track that can be added to the queue
 */
public interface Playable {

    String UNAVAILABLE_STRING = "[UNAVAILABLE]";
    String LOADING_STRING = "Loading...";

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
     * @return The title of the track. This is usually the song name without artist string.
     */
    String getTitle() throws UnavailableResourceException;

    default String title() {
        try {
            return getTitle();
        } catch (UnavailableResourceException e) {
            return UNAVAILABLE_STRING;
        }
    }

    String getTitle(long timeOut, TimeUnit unit) throws UnavailableResourceException, TimeoutException;

    default String title(long timeOut, TimeUnit unit) {
        try {
            return getTitle(timeOut, unit);
        } catch (UnavailableResourceException e) {
            return UNAVAILABLE_STRING;
        } catch (TimeoutException e) {
            return LOADING_STRING;
        }
    }

    String getTitleNow(String alternativeValue) throws UnavailableResourceException;

    default String getTitleNow() {
        try {
            return getTitleNow(LOADING_STRING);
        } catch (UnavailableResourceException e) {
            return UNAVAILABLE_STRING;
        }
    }

    /**
     * @return The title of the Playable. For Spotify it's the track name, for YouTube it's the video title and for other
     * URLs it's either the tile or the URL depending on whether or not a title could be found
     * @throws UnavailableResourceException if loading the item was cancelled due to being unavailable or cancelled
     */
    String getDisplay() throws UnavailableResourceException;

    /**
     * @return the display of the Playable, showing cancelled Playables as "[UNAVAILABLE]" and fall back to "[NO TITLE]"
     * if null.
     */
    default String display() {
        try {
            String display = getDisplay();
            return display != null ? display : "[NO TITLE]";
        } catch (UnavailableResourceException e) {
            return UNAVAILABLE_STRING;
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
            return UNAVAILABLE_STRING;
        } catch (TimeoutException e) {
            return LOADING_STRING;
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
            return getDisplayNow(LOADING_STRING);
        } catch (UnavailableResourceException e) {
            return UNAVAILABLE_STRING;
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
     * @return source of the Playable. Spotify, YouTube or URL.
     */
    Source getSource();

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

    /**
     * For lazy Playables that have not received values yet, this will tell the playable that it will be used soon and it
     * should start fetching data. This method makes no guarantee that this playable will be complete when this method
     * returns but it will have submitted the task to fetch data in a separate executor service. For example, this might
     * be used for the previous and next playables that are shown by the queue widget or the next playable shown by the
     * now playing widget. Where ever playables are not required right now but should be prioritised. This method returns
     * the current playable as it is intended for method chaining.
     * <p>
     * Exemplary use case:
     * <code>playable.fetch().display()</code>
     * This method is commonly used to prepare displaying relevant Playables without blocking the current thread.
     */
    default Playable fetch() {
        return this;
    }

    enum Source {

        SPOTIFY("Spotify"),
        YOUTUBE("YouTube"),
        URL("Url"),
        FILEBROKER("filebroker");

        private final String name;

        Source(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

    }

}
