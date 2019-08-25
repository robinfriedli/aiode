package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class EmptyCommand extends AbstractCommand {

    public EmptyCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, String identifier, String description) {
        super(commandContribution, context, commandManager, commandBody, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Playlist playlist = SearchEngine.searchLocalList(session, getCommandInput(), isPartitioned(), getContext().getGuild().getId());

        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No botify playlist found for '%s'", getCommandInput()));
        }

        if (playlist.isEmpty()) {
            throw new InvalidCommandException("Playlist is already empty");
        }

        invoke(() -> {
            for (PlaylistItem item : playlist.getItems()) {
                session.delete(item);
            }
        });
    }

    @Override
    public void onSuccess() {
    }
}
