package net.robinfriedli.botify.audio;

import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import org.hibernate.Session;

/**
 * Wrapper class for everything that can be added to the {@link AudioQueue}
 */
public interface Playable {

    /**
     * @return The url of the music file to stream
     * @throws InterruptedException if the thread loading the data asynchronously gets interrupted
     */
    String getPlaybackUrl() throws InterruptedException;

    /**
     * @return The human readable String to identify the track with. For random URLs just the url itself (for YouTube
     * videos or Spotify tracks its the title)
     * @throws InterruptedException if the thread loading the data asynchronously gets interrupted
     */
    String getDisplay() throws InterruptedException;

    default String getDisplayInterruptible() {
        try {
            return getDisplay();
        } catch (InterruptedException e) {
            return "[UNAVAILABLE]";
        }
    }

    /**
     * @return The duration of the audio track in milliseconds
     * @throws InterruptedException if the thread loading the data asynchronously gets interrupted
     */
    long getDurationMs() throws InterruptedException;

    default long getDurationMsInterruptible() {
        try {
            return getDurationMs();
        } catch (InterruptedException e) {
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

}
