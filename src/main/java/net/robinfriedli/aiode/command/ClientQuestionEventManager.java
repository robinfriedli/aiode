package net.robinfriedli.aiode.command;

import java.util.List;
import java.util.Optional;

import com.google.api.client.util.Lists;

public class ClientQuestionEventManager {

    /**
     * all unanswered Questions. Questions get removed after 5 minutes or after the same user enters a different command
     * that triggers a question.
     */
    private final List<ClientQuestionEvent> pendingQuestions = Lists.newArrayList();

    public synchronized void addQuestion(ClientQuestionEvent question) {
        Optional<ClientQuestionEvent> existingQuestion = getQuestion(question.getCommandContext());
        existingQuestion.ifPresent(ClientQuestionEvent::destroy);

        pendingQuestions.add(question);
    }

    public synchronized void removeQuestion(ClientQuestionEvent question) {
        pendingQuestions.remove(question);
    }

    public synchronized Optional<ClientQuestionEvent> getQuestion(CommandContext commandContext) {
        return pendingQuestions
            .stream()
            .filter(question -> question.getUser().getId().equals(commandContext.getUser().getId()))
            .findFirst();
    }

}
