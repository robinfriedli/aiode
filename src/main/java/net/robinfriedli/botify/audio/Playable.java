package net.robinfriedli.botify.audio;

import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

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

    /**
     * @return The duration of the audio track in milliseconds
     * @throws InterruptedException if the thread loading the data asynchronously gets interrupted
     */
    long getDurationMs() throws InterruptedException;

    /**
     * @param context the context for the XML file
     * @param user the user that adds the element
     * @return an XML element with the data for this playable to save it in a playlist
     */
    XmlElement export(Context context, User user);

}
