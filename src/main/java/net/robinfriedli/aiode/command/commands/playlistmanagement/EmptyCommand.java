package net.robinfriedli.aiode.command.commands.playlistmanagement;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import net.robinfriedli.aiode.util.SearchEngine;
import org.hibernate.Session;

public class EmptyCommand extends AbstractCommand {

    public EmptyCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Playlist playlist = SearchEngine.searchLocalList(session, getCommandInput());

        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No aiode playlist found for '%s'", getCommandInput()));
        }

        if (playlist.isEmpty()) {
            throw new InvalidCommandException("Playlist is already empty");
        }

        invoke(() -> {
            for (PlaylistItem item : playlist.getItems()) {
                session.remove(item);
            }
        });
    }

    @Override
    public void onSuccess() {
    }
}
