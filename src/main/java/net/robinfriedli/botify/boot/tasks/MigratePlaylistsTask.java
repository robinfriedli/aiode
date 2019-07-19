package net.robinfriedli.botify.boot.tasks;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.io.Files;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.interceptors.InterceptorChain;
import net.robinfriedli.botify.interceptors.PlaylistItemTimestampListener;
import net.robinfriedli.botify.interceptors.VerifyPlaylistListener;
import net.robinfriedli.botify.tasks.HibernatePlaylistMigrator;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Migrate the XML playlists to the database
 */
public class MigratePlaylistsTask implements StartupTask {

    private final JDA jda;
    private final JxpBackend jxpBackend;
    private final SessionFactory sessionFactory;
    private final SpotifyApi spotifyApi;

    public MigratePlaylistsTask(JDA jda, JxpBackend jxpBackend, SessionFactory sessionFactory, SpotifyApi spotifyApi) {
        this.jda = jda;
        this.jxpBackend = jxpBackend;
        this.sessionFactory = sessionFactory;
        this.spotifyApi = spotifyApi;
    }

    @Override
    public void perform() throws Exception {
        try (Session session = sessionFactory.withOptions().interceptor(InterceptorChain.of(
            PlaylistItemTimestampListener.class, VerifyPlaylistListener.class)).openSession()) {
            session.beginTransaction();
            for (Guild guild : jda.getGuilds()) {
                String path = PropertiesLoadingService.requireProperty("GUILD_PLAYLISTS_PATH", guild.getId());
                File xmlFile = new File(path);
                if (xmlFile.exists()) {
                    try (Context context = jxpBackend.getContext(xmlFile)) {
                        migrateFile(session, context, guild, spotifyApi);
                    }
                }
            }
            session.getTransaction().commit();
        }
    }

    private void migrateFile(Session session, Context context, Guild guild, SpotifyApi spotifyApi) throws
        IOException, SpotifyWebApiException {
        HibernatePlaylistMigrator hibernatePlaylistMigrator = new HibernatePlaylistMigrator(context, guild, spotifyApi, session);
        Map<Playlist, List<PlaylistItem>> playlistMap = hibernatePlaylistMigrator.perform();
        for (Playlist playlist : playlistMap.keySet()) {
            playlistMap.get(playlist).forEach(item -> {
                item.add();
                session.persist(item);
            });
            session.persist(playlist);
        }
        File directory = new File("./resources/archive");
        if (!directory.exists()) {
            directory.mkdir();
        }
        File file = context.getFile();
        Objects.requireNonNull(file);
        Files.move(file, new File(directory.getPath() + "/" + file.getName()));
    }

}
