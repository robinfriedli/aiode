package net.robinfriedli.aiode.command.commands.playlistmanagement;

import java.util.List;

import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import org.hibernate.Session;

public class SynchroniseCommand extends AddCommand {

    public SynchroniseCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category, "with");
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
                    session.remove(item);
                }
            }
            super.addToList(playlist, items);
        });
    }

    @Override
    public void onSuccess() {
    }

}
