package net.robinfriedli.botify.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.exec.PooledTrackLoadingExecutor;
import net.robinfriedli.botify.audio.exec.ReplaceableTrackLoadingExecutor;
import net.robinfriedli.botify.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.ClientQuestionEvent;
import net.robinfriedli.botify.command.ClientQuestionEventManager;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.discord.property.properties.BotNameProperty;
import net.robinfriedli.botify.discord.property.properties.PrefixProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import org.hibernate.Session;

/**
 * Provides context for a guild by storing and loading the guild's {@link GuildSpecification}, holding the guild's
 * {@link AudioPlayback} and guild specific {@link TrackLoadingExecutor} implementations to manage concurrent operations per guild.
 * Holds the managers for all open {@link ClientQuestionEvent} and active {@link AbstractWidget} for this guild.
 */
public class GuildContext {

    private final AudioPlayback playback;
    private final ClientQuestionEventManager clientQuestionEventManager;
    private final Guild guild;
    private final long specificationPk;
    private final PooledTrackLoadingExecutor pooledTrackLoadingExecutor;
    private final ReplaceableTrackLoadingExecutor replaceableTrackLoadingExecutor;
    private final WidgetManager widgetManager;

    public GuildContext(Guild guild, AudioPlayback playback, long specificationPk) {
        this.playback = playback;
        clientQuestionEventManager = new ClientQuestionEventManager();
        this.guild = guild;
        this.specificationPk = specificationPk;
        pooledTrackLoadingExecutor = new PooledTrackLoadingExecutor(guild.getId(), this);
        replaceableTrackLoadingExecutor = new ReplaceableTrackLoadingExecutor(this);
        widgetManager = new WidgetManager();
    }

    public Guild getGuild() {
        return guild;
    }

    public AudioPlayback getPlayback() {
        return playback;
    }

    public GuildSpecification getSpecification(Session session) {
        return session.load(GuildSpecification.class, specificationPk);
    }

    public GuildSpecification getSpecification() {
        ExecutionContext executionContext = ExecutionContext.Current.get();

        if (executionContext == null) {
            throw new IllegalStateException("No command context setup, session needs to be provided explicitly as operating on a potential proxy from a different session is unsafe");
        }

        return getSpecification(executionContext.getSession());
    }

    public String getBotName() {
        return StaticSessionProvider.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            GuildSpecification specification = getSpecification(session);

            return guildPropertyManager
                .getPropertyValueOptional("botName", String.class, specification)
                .orElse(BotNameProperty.DEFAULT_FALLBACK);
        });
    }

    public void setBotName(String name) {
        StaticSessionProvider.consumeSession(session -> {
            GuildSpecification guildSpecification = getSpecification(session);

            guildSpecification.setBotName(name);
        });
    }

    public String getPrefix() {
        return StaticSessionProvider.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            GuildSpecification specification = getSpecification(session);

            return guildPropertyManager
                .getPropertyValueOptional("prefix", String.class, specification)
                .orElse(PrefixProperty.DEFAULT_FALLBACK);
        });
    }

    public void setPrefix(String prefix) {
        StaticSessionProvider.consumeSession(session -> {
            GuildSpecification guildSpecification = getSpecification(session);

            guildSpecification.setPrefix(prefix);
        });
    }

    public PooledTrackLoadingExecutor getPooledTrackLoadingExecutor() {
        return pooledTrackLoadingExecutor;
    }

    public ReplaceableTrackLoadingExecutor getReplaceableTrackLoadingExecutor() {
        return replaceableTrackLoadingExecutor;
    }

    public WidgetManager getWidgetManager() {
        return widgetManager;
    }

    public ClientQuestionEventManager getClientQuestionEventManager() {
        return clientQuestionEventManager;
    }
}
