package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

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
