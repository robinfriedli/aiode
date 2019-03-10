package net.robinfriedli.botify.boot;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.JDA;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.Session;

/**
 * Interface for tasks that ensure the integrity of the XML configuration
 */
public interface StartupTask {

    /**
     * The task to run.
     *
     * @param jxpBackend the JXP backend managing all XML files
     * @param jda the discord api to retrieve any guild specific files
     */
    void perform(JxpBackend jxpBackend, JDA jda, SpotifyApi spotifyApi, YouTubeService youTubeService, Session session) throws Exception;

}
