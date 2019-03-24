package net.robinfriedli.botify.command.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ClientQuestionEvent;
import net.robinfriedli.botify.command.CommandInterceptor;
import net.robinfriedli.botify.command.commands.AnswerCommand;

public class PendingQuestionRemovalInterceptor implements CommandInterceptor {

    @Override
    public void intercept(AbstractCommand command) {
        if (!(command instanceof AnswerCommand)) {
            // if the user has a pending question, destroy
            command.getManager().getQuestion(command.getContext()).ifPresent(ClientQuestionEvent::destroy);
        }
    }
}
