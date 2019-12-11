package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.audio.exec.TrackLoadingExecutor;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public abstract class AbstractQueueLoadingCommand extends AbstractPlayableLoadingCommand {

    public AbstractQueueLoadingCommand(CommandContribution commandContribution,
                                       CommandContext context,
                                       CommandManager commandManager,
                                       String commandString,
                                       String identifier,
                                       String description,
                                       Category category,
                                       boolean mayInterrupt,
                                       TrackLoadingExecutor trackLoadingExecutor) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, category, mayInterrupt, trackLoadingExecutor);
    }

    @Override
    protected boolean shouldRedirectSpotify() {
        return !argumentSet("preview");
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = super.setupArguments();
        argumentContribution.map("preview").setRequiresInput(true)
            .setDescription("Load the short preview mp3 directly from Spotify instead of the full track from YouTube.")
            .addRule(ac -> {
                Source source = getSource();

                if (ac.argumentSet("list")) {
                    return source.isSpotify() || source.isLocal();
                }

                return source.isSpotify();
            }, "Argument 'preview' may only be used with Spotify.");
        return argumentContribution;
    }

}
