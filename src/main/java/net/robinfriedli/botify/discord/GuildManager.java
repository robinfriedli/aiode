package net.robinfriedli.botify.discord;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.botify.function.Invoker;
import net.robinfriedli.botify.interceptors.InterceptorChain;
import net.robinfriedli.botify.interceptors.PlaylistItemTimestampListener;
import net.robinfriedli.botify.interceptors.VerifyPlaylistListener;
import net.robinfriedli.botify.tasks.HibernatePlaylistMigrator;
import net.robinfriedli.botify.util.ISnowflakeMap;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Manages the {@link GuildContext} for all guilds.
 */
public class GuildManager {

    private final ISnowflakeMap<GuildContext> guildContexts = new ISnowflakeMap<>();
    private final Invoker internalInvoker;
    // invoker used in mode shared, so that all guilds are synchronised
    @Nullable
    private final Invoker sharedInvoker;
    private final JxpBackend jxpBackend;
    private final Logger logger;
    private final Mode mode;
    private AudioManager audioManager;

    public GuildManager(JxpBackend jxpBackend, Mode mode) {
        internalInvoker = new Invoker();
        this.jxpBackend = jxpBackend;
        logger = LoggerFactory.getLogger(getClass());
        this.mode = mode;
        if (mode == Mode.SHARED) {
            sharedInvoker = new Invoker();
        } else {
            sharedInvoker = null;
        }
    }

    public void addGuild(Guild guild) {
        initializeGuild(guild);
    }

    public void removeGuild(Guild guild) {
        guildContexts.remove(guild);
    }

    public String getNameForGuild(Guild guild) {
        return getContextForGuild(guild).getBotName();
    }

    @Nullable
    public String getPrefixForGuild(Guild guild) {
        return getContextForGuild(guild).getPrefix();
    }

    public boolean checkAccess(String commandIdentifier, Member member) {
        if (member.isOwner()) {
            return true;
        }

        AccessConfiguration accessConfiguration = getAccessConfiguration(commandIdentifier, member.getGuild());
        return accessConfiguration == null || accessConfiguration.canAccess(member);
    }

    @Nullable
    public AccessConfiguration getAccessConfiguration(String commandIdentifier, Guild guild) {
        return StaticSessionProvider.invokeWithSession(session -> {
            Optional<AccessConfiguration> accessConfiguration = session.createQuery("from " + AccessConfiguration.class.getName()
                    + " where command_identifier = '" + commandIdentifier + "' and"
                    + " guild_specification_pk = (select pk from " + GuildSpecification.class.getName() + " where guild_id = '" + guild.getId() + "')"
                , AccessConfiguration.class)
                .setCacheable(true)
                .uniqueResultOptional();
            return accessConfiguration.orElse(null);
        });
    }

    public GuildContext getContextForGuild(Guild guild) {
        GuildContext guildContext = guildContexts.get(guild);

        if (guildContext == null) {
            return initializeGuild(guild);
        }

        return guildContext;
    }

    public Set<Guild> getActiveGuilds(Session session) {
        // consider all guilds were active within the last 10 minutes to be active
        return getActiveGuilds(session, 600000);
    }

    /**
     * Return guilds that are active now (playing music) or were active withing the specified amount of milliseconds
     * (by entering a command or listening a song).
     *
     * @param session the hibernate session
     * @param delayMs the maximum amount of time since the last action for a guild to be considered active in milliseconds
     * @return all active guilds
     */
    public Set<Guild> getActiveGuilds(Session session, long delayMs) {
        JDA jda = Botify.get().getJda();
        Set<Guild> activeGuilds = Sets.newHashSet();
        Set<String> activeGuildIds = Sets.newHashSet();

        if (CommandContext.Current.isSet()) {
            activeGuilds.add(CommandContext.Current.require().getGuild());
        }

        for (Guild guild : jda.getGuilds()) {
            AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
            if (playback.isPlaying()) {
                activeGuilds.add(guild);
            }
        }

        CriteriaBuilder cb = session.getCriteriaBuilder();

        long startMillis = System.currentTimeMillis() - delayMs;
        CriteriaQuery<String> recentCommandGuildsQuery = cb.createQuery(String.class);
        Root<CommandHistory> commandsQueryRoot = recentCommandGuildsQuery.from(CommandHistory.class);
        recentCommandGuildsQuery
            .select(commandsQueryRoot.get("guildId"))
            .where(cb.greaterThan(commandsQueryRoot.get("startMillis"), startMillis));
        Set<String> recentCommandGuildIds = session.createQuery(recentCommandGuildsQuery).getResultStream().collect(Collectors.toSet());
        activeGuildIds.addAll(recentCommandGuildIds);

        LocalDateTime dateTime10MinutesAgo = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault());
        CriteriaQuery<String> recentPlaybackGuildsQuery = cb.createQuery(String.class);
        Root<PlaybackHistory> playbackHistoryRoot = recentPlaybackGuildsQuery.from(PlaybackHistory.class);
        recentPlaybackGuildsQuery
            .select(playbackHistoryRoot.get("guildId"))
            .where(cb.greaterThan(playbackHistoryRoot.get("timestamp"), dateTime10MinutesAgo));
        Set<String> recentPlaybackGuildIds = session.createQuery(recentPlaybackGuildsQuery).getResultStream().collect(Collectors.toSet());
        activeGuildIds.addAll(recentPlaybackGuildIds);

        for (String guildId : activeGuildIds) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                activeGuilds.add(guild);
            }
        }

        return activeGuilds;
    }

    public Collection<GuildContext> getGuildContexts() {
        return guildContexts.values();
    }

    public void setAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public Mode getMode() {
        return mode;
    }

    private GuildContext initializeGuild(Guild guild) {
        return StaticSessionProvider.invokeWithSession(session -> {
            AudioPlayer player = audioManager.getPlayerManager().createPlayer();

            Optional<Long> existingSpecification = session.createQuery("select pk from " + GuildSpecification.class.getName()
                + " where guildId = '" + guild.getId() + "'", Long.class).uniqueResultOptional();

            if (existingSpecification.isPresent()) {
                GuildContext guildContext = new GuildContext(guild, new AudioPlayback(player, guild), existingSpecification.get(), sharedInvoker);
                guildContexts.put(guild, guildContext);
                return guildContext;
            } else {
                GuildSpecification newSpecification = internalInvoker.invoke(session, () -> {
                    GuildSpecification specification = new GuildSpecification(guild);
                    AccessConfiguration permissionConfiguration = new AccessConfiguration("permission");
                    session.persist(permissionConfiguration);
                    specification.addAccessConfiguration(permissionConfiguration);
                    session.persist(specification);
                    return specification;
                });

                GuildContext guildContext = new GuildContext(guild, new AudioPlayback(player, guild), newSpecification.getPk(), sharedInvoker);
                guildContexts.put(guild, guildContext);

                handleNewGuild(guild);
                return guildContext;
            }
        });
    }

    private void handleNewGuild(Guild guild) {
        Botify botify = Botify.get();
        MessageService messageService = botify.getMessageService();
        try (Context context = jxpBackend.createLazyContext(PropertiesLoadingService.requireProperty("EMBED_DOCUMENTS_PATH"))) {
            EmbedDocumentContribution embedDocumentContribution = context
                .query(attribute("name").is("getting-started"), EmbedDocumentContribution.class)
                .requireOnlyResult();
            EmbedBuilder embedBuilder = embedDocumentContribution.buildEmbed();
            messageService.sendWithLogo(embedBuilder, guild);
        } catch (Throwable e) {
            logger.error("Error sending getting started message", e);
        }

        SessionFactory sessionFactory = StaticSessionProvider.getSessionFactory();
        SpotifyApi.Builder spotifyApiBuilder = botify.getSpotifyApiBuilder();
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
                } catch (Exception e) {
                    logger.error("Exception while migrating hibernate playlists", e);
                    session.close();
                    return;
                }

                Mode mode = getMode();
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

    public enum Mode {
        /**
         * All database entities will be the same for all guilds, meaning all guilds share the same playlists, presets etc.
         */
        SHARED,

        /**
         * Database queries will include the guild id to separate playlists, presets and other entities
         */
        PARTITIONED
    }

}
