package net.robinfriedli.botify.discord;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.api.client.util.Lists;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.ClientQuestionEvent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.concurrent.GuildTrackLoadingExecutor;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.function.Invoker;
import net.robinfriedli.botify.util.StaticSessionProvider;
import org.hibernate.Session;

/**
 * Provides context for a guild by storing and loading the guild's {@link GuildSpecification}, holding the guild's
 * {@link AudioPlayback} and {@link Invoker} / {@link GuildTrackLoadingExecutor} to manage concurrent operations per guild.
 * Holds all open {@link ClientQuestionEvent} for this guild.
 */
public class GuildContext {

    private final Guild guild;
    private final AudioPlayback playback;
    private final Invoker invoker;
    private final long specificationPk;
    private final GuildTrackLoadingExecutor trackLoadingExecutor;
    /**
     * all unanswered Questions. Questions get removed after 5 minutes or after the same user enters a different command
     * that triggers a question.
     */
    private final List<ClientQuestionEvent> pendingQuestions;

    public GuildContext(Guild guild, AudioPlayback playback, long specificationPk, @Nullable Invoker sharedInvoker) {
        this.guild = guild;
        this.playback = playback;
        this.specificationPk = specificationPk;
        invoker = sharedInvoker == null ? new Invoker() : sharedInvoker;
        trackLoadingExecutor = new GuildTrackLoadingExecutor(this);
        pendingQuestions = Lists.newArrayList();
    }

    public Guild getGuild() {
        return guild;
    }

    public AudioPlayback getPlayback() {
        return playback;
    }

    public Invoker getInvoker() {
        return invoker;
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
        StaticSessionProvider.invokeWithSession(session -> {
            GuildSpecification guildSpecification = getSpecification(session);

            getInvoker().invoke(session, () -> guildSpecification.setBotName(name));
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
        StaticSessionProvider.invokeWithSession(session -> {
            GuildSpecification guildSpecification = getSpecification(session);

            getInvoker().invoke(session, () -> guildSpecification.setPrefix(prefix));
        });
    }

    public void addQuestion(ClientQuestionEvent question) {
        Optional<ClientQuestionEvent> existingQuestion = getQuestion(question.getCommandContext());
        existingQuestion.ifPresent(ClientQuestionEvent::destroy);

        pendingQuestions.add(question);
    }

    public void removeQuestion(ClientQuestionEvent question) {
        pendingQuestions.remove(question);
    }

    public Optional<ClientQuestionEvent> getQuestion(CommandContext commandContext) {
        return pendingQuestions
            .stream()
            .filter(question -> question.getUser().getId().equals(commandContext.getUser().getId()))
            .findFirst();
    }

    public GuildTrackLoadingExecutor getTrackLoadingExecutor() {
        return trackLoadingExecutor;
    }
}
