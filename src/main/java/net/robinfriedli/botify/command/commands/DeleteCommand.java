package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class DeleteCommand extends AbstractCommand {

    public DeleteCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, false, false, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Context persistContext = getPersistContext();
        XmlElement playlist = persistContext.query(and(
            instanceOf(Playlist.class),
            attribute("name").fuzzyIs(getCommandBody())
        )).getOnlyResult();

        if (playlist == null) {
            throw new NoResultsFoundException("No local list found for " + getCommandBody());
        }

        persistContext.invoke(true, true, playlist::delete, getContext().getChannel());
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }
}
