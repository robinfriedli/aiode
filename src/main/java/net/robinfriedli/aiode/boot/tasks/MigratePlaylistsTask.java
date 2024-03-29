package net.robinfriedli.aiode.boot.tasks;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.common.io.Files;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.aiode.persist.interceptors.InterceptorChain;
import net.robinfriedli.aiode.persist.interceptors.PlaylistItemTimestampInterceptor;
import net.robinfriedli.aiode.persist.interceptors.VerifyPlaylistInterceptor;
import net.robinfriedli.aiode.persist.tasks.HibernatePlaylistMigrator;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Migrate the old XML playlists to the database for the v1.4 update
 */
public class MigratePlaylistsTask implements StartupTask {

    private final JxpBackend jxpBackend;
    private final SessionFactory sessionFactory;
    private final SpotifyApi spotifyApi;
    private final StartupTaskContribution contribution;

    public MigratePlaylistsTask(JxpBackend jxpBackend, SessionFactory sessionFactory, SpotifyApi spotifyApi, StartupTaskContribution contribution) {
        this.contribution = contribution;
        this.jxpBackend = jxpBackend;
        this.sessionFactory = sessionFactory;
        this.spotifyApi = spotifyApi;
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) throws Exception {
        try (Session session = sessionFactory.withOptions().interceptor(InterceptorChain.of(
            PlaylistItemTimestampInterceptor.class, VerifyPlaylistInterceptor.class)).openSession()) {
            session.beginTransaction();
            for (Guild guild : Objects.requireNonNull(shard).getGuilds()) {
                String path = String.format("./resources/%splaylists.xml", guild.getId());
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

    private void migrateFile(Session session, Context context, Guild guild, SpotifyApi spotifyApi) throws Exception {
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
