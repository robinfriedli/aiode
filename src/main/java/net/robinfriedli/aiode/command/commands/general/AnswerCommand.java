package net.robinfriedli.aiode.command.commands.general;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Splitter;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.CommandRuntimeException;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;

public class AnswerCommand extends AbstractCommand {

    private AbstractCommand targetCommand;

    public AnswerCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        getContext().getGuildContext().getClientQuestionEventManager().getQuestion(getContext()).ifPresentOrElse(question -> {
            AbstractCommand sourceCommand = question.getSourceCommand();

            String commandInput = getCommandInput();
            Splitter commaSplitter = Splitter.on(",").trimResults().omitEmptyStrings();
            List<String> options = commaSplitter.splitToList(commandInput);
            Object option;
            if (options.size() == 1) {
                option = question.get(options.get(0));
            } else {
                Set<Object> chosenOptions = new LinkedHashSet<>();
                for (String o : options) {
                    Object chosen = question.get(o);
                    if (chosen instanceof Collection) {
                        chosenOptions.addAll((Collection<?>) chosen);
                    } else {
                        chosenOptions.add(chosen);
                    }
                }

                option = chosenOptions;
            }
            try {
                targetCommand = sourceCommand.fork(getContext());
                targetCommand.withUserResponse(option);
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
        targetCommand.onSuccess();
    }

    public AbstractCommand getTargetCommand() {
        return targetCommand;
    }
}
