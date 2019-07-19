package net.robinfriedli.botify.discord;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.api.client.util.Lists;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.ClientQuestionEvent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.concurrent.GuildTrackLoadingExecutor;
import net.robinfriedli.botify.concurrent.Invoker;
import net.robinfriedli.botify.entities.GuildSpecification;

public class GuildContext {

    private final AudioPlayback playback;
    private final Invoker invoker;
    private final GuildSpecification specification;
    private final GuildTrackLoadingExecutor trackLoadingExecutor;
    /**
     * all unanswered Questions. Questions get removed after 5 minutes or after the same user enters a different command.
     */
    private final List<ClientQuestionEvent> pendingQuestions;

    public GuildContext(AudioPlayback playback, GuildSpecification specification, @Nullable Invoker sharedInvoker) {
        this.playback = playback;
        this.specification = specification;
        invoker = sharedInvoker == null ? new Invoker() : sharedInvoker;
        trackLoadingExecutor = new GuildTrackLoadingExecutor(this);
        pendingQuestions = Lists.newArrayList();
    }

    public AudioPlayback getPlayback() {
        return playback;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public GuildSpecification getSpecification() {
        return specification;
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
