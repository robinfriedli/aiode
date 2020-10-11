package net.robinfriedli.botify.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.ClientQuestionEvent;
import net.robinfriedli.botify.command.ClientQuestionEventManager;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.concurrent.GuildTrackLoadingExecutor;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.util.StaticSessionProvider;
import org.hibernate.Session;

/**
 * Provides context for a guild by storing and loading the guild's {@link GuildSpecification}, holding the guild's
 * {@link AudioPlayback} and {@link GuildTrackLoadingExecutor} to manage concurrent operations per guild.
 * Holds the managers for all open {@link ClientQuestionEvent} and active {@link AbstractWidget} for this guild.
 */
public class GuildContext {

    private final Guild guild;
    private final AudioPlayback playback;
    private final long specificationPk;
    private final GuildTrackLoadingExecutor trackLoadingExecutor;
    private final WidgetManager widgetManager;
    private final ClientQuestionEventManager clientQuestionEventManager;

    public GuildContext(Guild guild, AudioPlayback playback, long specificationPk) {
        this.guild = guild;
        this.playback = playback;
        this.specificationPk = specificationPk;
        trackLoadingExecutor = new GuildTrackLoadingExecutor(this);
        widgetManager = new WidgetManager();
        clientQuestionEventManager = new ClientQuestionEventManager();
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
        CommandContext commandContext = CommandContext.Current.get();

        if (commandContext == null) {
            throw new IllegalStateException("No command context setup, session needs to be provided explicitly as operating on a potential proxy from a different session is unsafe");
        }

        return getSpecification(commandContext.getSession());
    }

    public String getBotName() {
        return StaticSessionProvider.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            GuildSpecification specification = getSpecification(session);
            AbstractGuildProperty botName = guildPropertyManager.getProperty("botName");

            if (botName != null) {
                return (String) botName.get(specification);
            }

            return specification.getBotName();
        });
    }

    public boolean setBotName(String name) {
        StaticSessionProvider.consumeSession(session -> {
            GuildSpecification guildSpecification = getSpecification(session);

            HibernateInvoker.create().invoke(() -> guildSpecification.setBotName(name));
        });
        try {
            guild.getSelfMember().modifyNickname(name).queue();
            return true;
        } catch (InsufficientPermissionException ignored) {
            return false;
        }
    }

    public String getPrefix() {
        return StaticSessionProvider.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
            GuildSpecification specification = getSpecification(session);
            AbstractGuildProperty prefix = guildPropertyManager.getProperty("prefix");

            if (prefix != null) {
                return (String) prefix.get(specification);
            }

            return specification.getPrefix();
        });
    }

    public void setPrefix(String prefix) {
        StaticSessionProvider.consumeSession(session -> {
            GuildSpecification guildSpecification = getSpecification(session);

            HibernateInvoker.create().invoke(() -> guildSpecification.setPrefix(prefix));
        });
    }

    public GuildTrackLoadingExecutor getTrackLoadingExecutor() {
        return trackLoadingExecutor;
    }

    public WidgetManager getWidgetManager() {
        return widgetManager;
    }

    public ClientQuestionEventManager getClientQuestionEventManager() {
        return clientQuestionEventManager;
    }
}
