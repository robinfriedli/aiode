package net.robinfriedli.aiode.command.commands.playlistmanagement;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import net.robinfriedli.aiode.util.SearchEngine;
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
            playlist.getItems().forEach(session::remove);
            session.remove(playlist);
        });
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }
}
