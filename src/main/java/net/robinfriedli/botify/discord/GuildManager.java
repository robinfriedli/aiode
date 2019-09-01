package net.robinfriedli.botify.discord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.google.common.collect.Sets;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.function.Invoker;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.util.ISnowflakeMap;
import net.robinfriedli.botify.util.StaticSessionProvider;
import org.hibernate.Session;

/**
 * Manages the {@link GuildContext} for all guilds.
 */
public class GuildManager {

    private final ISnowflakeMap<GuildContext> guildContexts = new ISnowflakeMap<>();
    private final Invoker internalInvoker;
    // invoker used in mode shared, so that all guilds are synchronised
    @Nullable
    private final Invoker sharedInvoker;
    private final Mode mode;
    private AudioManager audioManager;

    public GuildManager(Mode mode) {
        internalInvoker = new Invoker();
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

        // consider all guilds that issued a command within the last 10 minutes to be active
        long millis10MinutesAgo = System.currentTimeMillis() - 600000;
        CriteriaQuery<String> recentCommandGuildsQuery = cb.createQuery(String.class);
        Root<CommandHistory> commandsQueryRoot = recentCommandGuildsQuery.from(CommandHistory.class);
        recentCommandGuildsQuery
            .select(commandsQueryRoot.get("guildId"))
            .where(cb.greaterThan(commandsQueryRoot.get("startMillis"), millis10MinutesAgo));
        Set<String> recentCommandGuildIds = session.createQuery(recentCommandGuildsQuery).getResultStream().collect(Collectors.toSet());
        activeGuildIds.addAll(recentCommandGuildIds);

        // consider all guilds that played a track withing the last 10 minutes to be active
        LocalDateTime dateTime10MinutesAgo = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis10MinutesAgo), ZoneId.systemDefault());
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

                return guildContext;
            }
        });
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
