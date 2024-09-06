package net.robinfriedli.aiode.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.exec.PooledTrackLoadingExecutor;
import net.robinfriedli.aiode.audio.exec.ReplaceableTrackLoadingExecutor;
import net.robinfriedli.aiode.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.aiode.command.ClientQuestionEvent;
import net.robinfriedli.aiode.command.ClientQuestionEventManager;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.discord.property.properties.BotNameProperty;
import net.robinfriedli.aiode.discord.property.properties.PrefixProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import org.hibernate.Session;
import org.jetbrains.annotations.Nullable;

/**
 * Provides context for a guild by storing and loading the guild's {@link GuildSpecification}, holding the guild's
 * {@link AudioPlayback} and guild specific {@link TrackLoadingExecutor} implementations to manage concurrent operations per guild.
 * Holds the managers for all open {@link ClientQuestionEvent} and active {@link AbstractWidget} for this guild.
 */
public class GuildContext {

    private final AudioPlayback playback;
    private final ClientQuestionEventManager clientQuestionEventManager;
    private final DiscordEntity.Guild guild;
    private final long specificationPk;
    private final PooledTrackLoadingExecutor pooledTrackLoadingExecutor;
    private final ReplaceableTrackLoadingExecutor replaceableTrackLoadingExecutor;
    private final WidgetRegistry widgetRegistry;

    public GuildContext(Guild guild, AudioPlayback playback, long specificationPk) {
        this.playback = playback;
        clientQuestionEventManager = new ClientQuestionEventManager();
        this.guild = new DiscordEntity.Guild(guild);
        this.specificationPk = specificationPk;
        pooledTrackLoadingExecutor = new PooledTrackLoadingExecutor(guild.getId(), this);
        replaceableTrackLoadingExecutor = new ReplaceableTrackLoadingExecutor(this);
        widgetRegistry = new WidgetRegistry();
    }

    public Guild getGuild() {
        return guild.get();
    }

    @Nullable
    public Guild retrieveGuild() {
        return guild.retrieve();
    }

    public AudioPlayback getPlayback() {
        return playback;
    }

    public GuildSpecification getSpecification(Session session) {
        GuildSpecification guildSpecification = session.getReference(GuildSpecification.class, specificationPk);
        session.refresh(guildSpecification);
        return guildSpecification;
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
            GuildPropertyManager guildPropertyManager = Aiode.get().getGuildPropertyManager();
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
            GuildPropertyManager guildPropertyManager = Aiode.get().getGuildPropertyManager();
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

    public WidgetRegistry getWidgetRegistry() {
        return widgetRegistry;
    }

    public ClientQuestionEventManager getClientQuestionEventManager() {
        return clientQuestionEventManager;
    }
}
