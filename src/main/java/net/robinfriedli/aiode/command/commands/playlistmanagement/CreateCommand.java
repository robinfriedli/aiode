package net.robinfriedli.aiode.command.commands.playlistmanagement;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.util.SearchEngine;
import org.hibernate.Session;

public class CreateCommand extends AbstractCommand {

    public CreateCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
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
