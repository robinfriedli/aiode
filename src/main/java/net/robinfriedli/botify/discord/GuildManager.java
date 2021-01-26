package net.robinfriedli.botify.discord;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.persist.interceptors.InterceptorChain;
import net.robinfriedli.botify.persist.interceptors.PlaylistItemTimestampInterceptor;
import net.robinfriedli.botify.persist.interceptors.VerifyPlaylistInterceptor;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.persist.tasks.HibernatePlaylistMigrator;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.SnowflakeMap;
import net.robinfriedli.exec.MutexSync;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Manages the {@link GuildContext} for all guilds.
 */
@Component
public class GuildManager {

    private final CommandManager commandManager;
    private final Context embedDocumentContext;
    @Nullable
    private final Context defaultPlaylistContext;
    private final HibernateComponent hibernateComponent;
    private final SnowflakeMap<GuildContext> guildContexts = new SnowflakeMap<>();
    private final Logger logger;
    private final Mode mode;
    private final MutexSync<Long> guildSetupSync;
    private final QueryBuilderFactory queryBuilderFactory;
    private AudioManager audioManager;

    public GuildManager(CommandManager commandManager,
                        @Value("classpath:xml-contributions/embedDocuments.xml") Resource embedDocumentsResource,
                        @Value("classpath:playlists.xml") Resource playlistsResource,
                        HibernateComponent hibernateComponent,
                        JxpBackend jxpBackend,
                        @Value("${botify.preferences.mode_partitioned}") boolean modePartitioned,
                        QueryBuilderFactory queryBuilderFactory) {
        this.commandManager = commandManager;
        try {
            embedDocumentContext = jxpBackend.createLazyContext(embedDocumentsResource.getInputStream());
            if (playlistsResource.exists()) {
                defaultPlaylistContext = jxpBackend.createLazyContext(playlistsResource.getInputStream());
            } else {
                defaultPlaylistContext = null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
        this.hibernateComponent = hibernateComponent;
        logger = LoggerFactory.getLogger(getClass());
        this.mode = modePartitioned ? GuildManager.Mode.PARTITIONED : GuildManager.Mode.SHARED;
        guildSetupSync = new MutexSync<>();
        this.queryBuilderFactory = queryBuilderFactory;
    }

    public void addGuild(Guild guild) {
        guildSetupSync.evaluate(guild.getIdLong(), () -> initializeGuild(guild));
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

    public GuildContext getContextForGuild(Guild guild) {
        GuildContext guildContext = guildContexts.get(guild);

        if (guildContext == null) {
            return guildSetupSync.evaluate(guild.getIdLong(), () -> {
                // if another thread is currently setting up the guild the guild context will have been created once
                // the synchronisation lock has been acquired
                GuildContext recheck = guildContexts.get(guild);
                if (recheck != null) {
                    return recheck;
                }

                return initializeGuild(guild);
            });
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
        Botify botify = Botify.get();
        ShardManager shardManager = botify.getShardManager();
        QueryBuilderFactory queryBuilderFactory = botify.getQueryBuilderFactory();
        Set<Guild> activeGuilds = Sets.newHashSet();
        Set<String> activeGuildIds = Sets.newHashSet();

        if (ExecutionContext.Current.isSet()) {
            activeGuilds.add(ExecutionContext.Current.require().getGuild());
        }

        for (Guild guild : shardManager.getGuilds()) {
            AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
            if (playback.isPlaying()) {
                activeGuilds.add(guild);
            }
        }

        long startMillis = System.currentTimeMillis() - delayMs;
        Set<String> recentCommandGuildIds = queryBuilderFactory.select(CommandHistory.class, "guildId", String.class)
            .where((cb, root) -> cb.greaterThan(root.get("startMillis"), startMillis))
            .build(session)
            .getResultStream().collect(Collectors.toSet());
        activeGuildIds.addAll(recentCommandGuildIds);

        LocalDateTime dateTime10MinutesAgo = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault());
        Set<String> recentPlaybackGuildIds = queryBuilderFactory.select(PlaybackHistory.class, "guildId", String.class)
            .where((cb, root) -> cb.greaterThan(root.get("timestamp"), dateTime10MinutesAgo))
            .build(session)
            .getResultStream().collect(Collectors.toSet());
        activeGuildIds.addAll(recentPlaybackGuildIds);

        for (String guildId : activeGuildIds) {
            Guild guild = shardManager.getGuildById(guildId);
            if (guild != null) {
                activeGuilds.add(guild);
            }
        }

        return activeGuilds;
    }

    public TextChannel getDefaultTextChannelForGuild(Guild guild) {
        GuildContext guildContext = getContextForGuild(guild);
        return getDefaultTextChannelForGuild(guild, guildContext);
    }

    public Set<GuildContext> getGuildContexts() {
        return Sets.newHashSet(guildContexts.values());
    }

    public void setAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public Mode getMode() {
        return mode;
    }

    private GuildContext initializeGuild(Guild guild) {
        GuildContext createdContext = hibernateComponent.invokeWithSession(session -> {
            AudioPlayer player = audioManager.getPlayerManager().createPlayer();

            Optional<Long> existingSpecification = queryBuilderFactory.select(GuildSpecification.class, "pk", Long.class)
                .where((cb, root) -> cb.equal(root.get("guildId"), guild.getId()))
                .build(session)
                .uniqueResultOptional();

            if (existingSpecification.isPresent()) {
                return new GuildContext(guild, new AudioPlayback(player, guild), existingSpecification.get());
            } else {
                GuildSpecification newSpecification = new GuildSpecification(guild);
                session.persist(newSpecification);
                commandManager.getCommandContributionContext()
                    .query(attribute("restrictedAccess").is(true), CommandContribution.class)
                    .getResultStream()
                    .forEach(restrictedCommand -> {
                        AccessConfiguration permissionConfiguration = new AccessConfiguration(restrictedCommand, session);
                        session.persist(permissionConfiguration);
                        newSpecification.addAccessConfiguration(permissionConfiguration);
                    });
                session.flush();

                GuildContext guildContext = new GuildContext(guild, new AudioPlayback(player, guild), newSpecification.getPk());

                handleNewGuild(guild, guildContext);
                return guildContext;
            }
        });
        guildContexts.put(guild, createdContext);
        return createdContext;
    }

    private void handleNewGuild(Guild guild, GuildContext guildContext) {
        Botify botify = Botify.get();
        MessageService messageService = botify.getMessageService();
        try {
            EmbedDocumentContribution embedDocumentContribution = embedDocumentContext
                .query(attribute("name").is("getting-started"), EmbedDocumentContribution.class)
                .getOnlyResult();
            if (embedDocumentContribution != null) {
                EmbedBuilder embedBuilder = embedDocumentContribution.buildEmbed();
                messageService.sendWithLogo(embedBuilder, getDefaultTextChannelForGuild(guild, guildContext));
            }
        } catch (Exception e) {
            logger.error("Error sending getting started message", e);
        }

        SessionFactory sessionFactory = hibernateComponent.getSessionFactory();
        SpotifyApi.Builder spotifyApiBuilder = botify.getSpotifyApiBuilder();
        if (defaultPlaylistContext != null) {
            try (Session session = sessionFactory.withOptions().interceptor(InterceptorChain.of(
                PlaylistItemTimestampInterceptor.class, VerifyPlaylistInterceptor.class)).openSession()) {
                HibernatePlaylistMigrator hibernatePlaylistMigrator = new HibernatePlaylistMigrator(defaultPlaylistContext, guild, spotifyApiBuilder.build(), session);
                Map<Playlist, List<PlaylistItem>> playlistMap = hibernatePlaylistMigrator.perform();

                Mode mode = getMode();
                HibernateInvoker.create(session).invokeConsumer(currentSession -> {
                    for (Playlist playlist : playlistMap.keySet()) {
                        Playlist existingList = SearchEngine.searchLocalList(currentSession, playlist.getName(), mode == GuildManager.Mode.PARTITIONED, guild.getId());
                        if (existingList == null) {
                            playlistMap.get(playlist).forEach(item -> {
                                item.add();
                                currentSession.persist(item);
                            });
                            currentSession.persist(playlist);
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Exception while setting up default playlists", e);
            }
        }
    }

    private TextChannel getDefaultTextChannelForGuild(Guild guild, GuildContext guildContext) {
        Botify botify = Botify.get();
        Member selfMember = guild.getSelfMember();

        // fetch the default text channel from the customised property
        GuildPropertyManager guildPropertyManager = botify.getGuildPropertyManager();
        AbstractGuildProperty defaultTextChannelProperty = guildPropertyManager.getProperty("defaultTextChannelId");
        if (defaultTextChannelProperty != null) {
            String defaultTextChannelId;
            try {
                defaultTextChannelId = (String) hibernateComponent.invokeWithSession(session -> defaultTextChannelProperty.get(guildContext.getSpecification(session)));
            } catch (NullPointerException e) {
                throw new RuntimeException(e);
            }

            if (!Strings.isNullOrEmpty(defaultTextChannelId)) {
                TextChannel textChannelById = guild.getTextChannelById(defaultTextChannelId);
                if (textChannelById != null && selfMember.hasAccess(textChannelById) && textChannelById.canTalk(selfMember)) {
                    return textChannelById;
                }
            }
        }

        // check if the guild's playback has a current communication text channel
        MessageChannel playbackCommunicationChannel = guildContext.getPlayback().getCommunicationChannel();
        if (playbackCommunicationChannel instanceof TextChannel) {
            TextChannel textChannelFromPlayback = (TextChannel) playbackCommunicationChannel;
            if (selfMember.hasAccess(textChannelFromPlayback) && textChannelFromPlayback.canTalk(selfMember)) {
                return textChannelFromPlayback;
            }
        }

        // use guild default defined by discord
        TextChannel defaultChannel = guild.getDefaultChannel();
        if (defaultChannel != null && selfMember.hasAccess(defaultChannel) && defaultChannel.canTalk(selfMember)) {
            return defaultChannel;
        } else {
            TextChannel systemChannel = guild.getSystemChannel();
            if (systemChannel != null && selfMember.hasAccess(systemChannel) && systemChannel.canTalk()) {
                return systemChannel;
            }
        }

        List<TextChannel> availableChannels = guild
            .getTextChannels()
            .stream()
            .filter(channel -> selfMember.hasAccess(channel) && channel.canTalk(selfMember))
            .collect(Collectors.toList());

        if (availableChannels.isEmpty()) {
            return null;
        } else {
            return availableChannels.get(0);
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
