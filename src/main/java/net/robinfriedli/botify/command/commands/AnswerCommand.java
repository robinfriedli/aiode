package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class AnswerCommand extends AbstractCommand {

    private AbstractCommand sourceCommand;

    public AnswerCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, true, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        getManager().getQuestion(getContext()).ifPresentOrElse(question -> {
            sourceCommand = question.getSourceCommand();

            Object option = question.get(getCommandBody());
            try {
                sourceCommand.withUserResponse(option);
                question.destroy();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CommandRuntimeException(e);
            }
        }, () -> {
            throw new InvalidCommandException("I don't remember asking you anything " + getContext().getUser().getName());
        });
    }

    @Override
    public void onSuccess() {
        sourceCommand.onSuccess();
    }
}
