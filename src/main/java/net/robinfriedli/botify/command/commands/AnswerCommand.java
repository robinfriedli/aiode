package net.robinfriedli.botify.command.commands;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Splitter;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class AnswerCommand extends AbstractCommand {

    private AbstractCommand sourceCommand;

    public AnswerCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        getContext().getGuildContext().getQuestion(getContext()).ifPresentOrElse(question -> {
            sourceCommand = question.getSourceCommand();

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
                        chosenOptions.addAll((Collection) chosen);
                    } else {
                        chosenOptions.add(chosen);
                    }
                }

                option = chosenOptions;
            }
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
