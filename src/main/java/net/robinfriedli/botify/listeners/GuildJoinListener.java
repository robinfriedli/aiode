package net.robinfriedli.botify.listeners;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.botify.interceptors.InterceptorChain;
import net.robinfriedli.botify.interceptors.PlaylistItemTimestampListener;
import net.robinfriedli.botify.interceptors.VerifyPlaylistListener;
import net.robinfriedli.botify.tasks.HibernatePlaylistMigrator;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.discordbots.api.client.DiscordBotListAPI;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jetbrains.annotations.NotNull;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Listener responsible for handling the bot joining or leaving a guild
 */
public class GuildJoinListener extends ListenerAdapter {

    private final CommandExecutionQueueManager executionQueueManager;
    @Nullable
    private final DiscordBotListAPI discordBotListAPI;
    private final GuildManager guildManager;
    private final JxpBackend jxpBackend;
    private final Logger logger;
    private final MessageService messageService;
    private final SessionFactory sessionFactory;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public GuildJoinListener(CommandExecutionQueueManager executionQueueManager,
                             @Nullable DiscordBotListAPI discordBotListAPI,
                             GuildManager guildManager,
                             JxpBackend jxpBackend,
                             MessageService messageService,
                             SessionFactory sessionFactory,
                             SpotifyApi.Builder spotifyApiBuilder) {
        this.executionQueueManager = executionQueueManager;
        this.discordBotListAPI = discordBotListAPI;
        this.guildManager = guildManager;
        this.jxpBackend = jxpBackend;
        this.messageService = messageService;
        this.sessionFactory = sessionFactory;
        this.spotifyApiBuilder = spotifyApiBuilder;
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        guildManager.addGuild(guild);
        executionQueueManager.addGuild(guild);

        try (Context context = jxpBackend.createLazyContext(PropertiesLoadingService.requireProperty("EMBED_DOCUMENTS_PATH"))) {
            EmbedDocumentContribution embedDocumentContribution = context
                .query(attribute("name").is("getting-started"), EmbedDocumentContribution.class)
                .requireOnlyResult();
            EmbedBuilder embedBuilder = embedDocumentContribution.buildEmbed();
            messageService.sendWithLogo(embedBuilder, guild);
        } catch (Throwable e) {
            logger.error("Error sending getting started message", e);
        }

        if (discordBotListAPI != null) {
            discordBotListAPI.setStats(event.getJDA().getGuilds().size());
        }

        try (Session session = sessionFactory.withOptions().interceptor(InterceptorChain.of(
            PlaylistItemTimestampListener.class, VerifyPlaylistListener.class)).openSession()) {
            String playlistsPath = PropertiesLoadingService.requireProperty("PLAYLISTS_PATH");
            File file = new File(playlistsPath);
            if (file.exists()) {
                Context context = jxpBackend.getContext(file);
                HibernatePlaylistMigrator hibernatePlaylistMigrator = new HibernatePlaylistMigrator(context, guild, spotifyApiBuilder.build(), session);
                Map<Playlist, List<PlaylistItem>> playlistMap;
                try {
                    playlistMap = hibernatePlaylistMigrator.perform();
                } catch (IOException | SpotifyWebApiException e) {
                    logger.error("Exception while migrating hibernate playlists", e);
                    session.close();
                    return;
                }

                GuildManager.Mode mode = guildManager.getMode();
                session.beginTransaction();
                for (Playlist playlist : playlistMap.keySet()) {
                    Playlist existingList = SearchEngine.searchLocalList(session, playlist.getName(), mode == GuildManager.Mode.PARTITIONED, guild.getId());
                    if (existingList == null) {
                        playlistMap.get(playlist).forEach(item -> {
                            item.add();
                            session.persist(item);
                        });
                        session.persist(playlist);
                    }
                }
                session.getTransaction().commit();
            }
        }
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        if (discordBotListAPI != null) {
            discordBotListAPI.setStats(event.getJDA().getGuilds().size());
        }
    }

}
