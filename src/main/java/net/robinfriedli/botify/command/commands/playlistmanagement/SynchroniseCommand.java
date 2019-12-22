package net.robinfriedli.botify.command.commands.playlistmanagement;

import java.util.List;

import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import org.hibernate.Session;

public class SynchroniseCommand extends AddCommand {

    public SynchroniseCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, String identifier, String description) {
        super(commandContribution, context, commandManager, commandBody, identifier, description, "with");
    }

    @Override
    protected void addToList(Playlist playlist, List<PlaylistItem> items) {
        if (items.isEmpty()) {
            throw new NoResultsFoundException("Result is empty!");
        }

        invoke(() -> {
            if (!playlist.isEmpty()) {
                Session session = getContext().getSession();
                for (PlaylistItem item : playlist.getItems()) {
                    session.delete(item);
                }
            }
            super.addToList(playlist, items);
        });
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = super.setupArguments();
        argumentContribution.map("with")
            .setDescription("Specify the remote playlist to synchronise the local list with.");
        argumentContribution.remove("to");
        return argumentContribution;
    }
}
