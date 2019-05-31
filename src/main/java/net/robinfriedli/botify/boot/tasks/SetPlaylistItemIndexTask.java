package net.robinfriedli.botify.boot.tasks;

import java.util.List;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.JDA;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.tasks.UpdatePlaylistItemIndicesTask;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * Migration for update 1.5.2 setting the values for the new field item_index
 */
public class SetPlaylistItemIndexTask implements StartupTask {

    @Override
    public void perform(JxpBackend jxpBackend, JDA jda, SpotifyApi spotifyApi, YouTubeService youTubeService, Session session) {
        Query<Playlist> relevantPlaylistQuery = session.createQuery("from " + Playlist.class.getName() + " as p where " +
            "exists(from " + Song.class.getName() + " where item_index = null and playlist_pk = p.pk) or " +
            "exists(from " + Video.class.getName() + " where item_index = null and playlist_pk = p.pk) or " +
            "exists(from " + UrlTrack.class.getName() + " where item_index = null and playlist_pk = p.pk)", Playlist.class);
        List<Playlist> relevantPlaylists = relevantPlaylistQuery.getResultList();

        session.beginTransaction();
        UpdatePlaylistItemIndicesTask task = new UpdatePlaylistItemIndicesTask(relevantPlaylists);
        task.perform();
        session.getTransaction().commit();
    }
}
