package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class DeleteCommand extends AbstractCommand {

    public DeleteCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Playlist playlist = SearchEngine.searchLocalList(session, getCommandBody(), isPartitioned(), getContext().getGuild().getId());

        if (playlist == null) {
            throw new NoResultsFoundException("No local list found for " + getCommandBody());
        }

        invoke(() -> {
            playlist.getItemsSorted().forEach(session::delete);
            session.delete(playlist);
        });
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }
}
