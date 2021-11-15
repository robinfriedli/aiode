package net.robinfriedli.aiode.command.commands;

import net.robinfriedli.aiode.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

public abstract class AbstractQueueLoadingCommand extends AbstractPlayableLoadingCommand {

    public AbstractQueueLoadingCommand(CommandContribution commandContribution,
                                       CommandContext context,
                                       CommandManager commandManager,
                                       String commandString,
                                       boolean requiresInput,
                                       String identifier,
                                       String description,
                                       Category category,
                                       boolean mayInterrupt,
                                       TrackLoadingExecutor trackLoadingExecutor) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category, mayInterrupt, trackLoadingExecutor);
    }

    @Override
    protected boolean shouldRedirectSpotify() {
        return !argumentSet("preview");
    }

}
