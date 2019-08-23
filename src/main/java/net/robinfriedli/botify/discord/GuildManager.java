package net.robinfriedli.botify.discord;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nullable;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.concurrent.Invoker;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.util.ISnowflakeMap;
import net.robinfriedli.botify.util.StaticSessionProvider;

/**
 * Manages the {@link GuildContext} for all guilds.
 */
public class GuildManager {

    private final GuildPropertyManager guildPropertyManager;
    private final ISnowflakeMap<GuildContext> guildContexts = new ISnowflakeMap<>();
    private final Invoker internalInvoker;
    // invoker used in mode shared, so that all guilds are synchronised
    @Nullable
    private final Invoker sharedInvoker;
    private final Mode mode;
    private AudioManager audioManager;

    public GuildManager(GuildPropertyManager guildPropertyManager, Mode mode) {
        this.guildPropertyManager = guildPropertyManager;
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
                session.beginTransaction();
                GuildSpecification newSpecification = internalInvoker.invoke(session, () -> {
                    GuildSpecification specification = new GuildSpecification(guild);
                    AccessConfiguration permissionConfiguration = new AccessConfiguration("permission");
                    session.persist(permissionConfiguration);
                    specification.addAccessConfiguration(permissionConfiguration);
                    session.persist(specification);
                    return specification;
                });
                session.getTransaction().commit();

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
