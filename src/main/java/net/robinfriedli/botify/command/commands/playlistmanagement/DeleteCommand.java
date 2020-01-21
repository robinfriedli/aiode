package net.robinfriedli.botify.command.commands.playlistmanagement;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class DeleteCommand extends AbstractCommand {

    public DeleteCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Playlist playlist = SearchEngine.searchLocalList(session, getCommandInput());

        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local list found for '%s'", getCommandInput()));
        }

        invoke(() -> {
            playlist.getItems().forEach(session::delete);
            session.delete(playlist);
        });
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }
}
