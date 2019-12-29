package net.robinfriedli.botify.command.commands.playlistmanagement;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class CreateCommand extends AbstractCommand {

    public CreateCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Playlist existingPlaylist = SearchEngine.searchLocalList(session, getCommandInput());

        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandInput() + " already exists");
        }

        Playlist playlist = new Playlist(getCommandInput(), getContext().getUser(), getContext().getGuild());
        invoke(() -> session.persist(playlist));
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }
}
