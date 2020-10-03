package net.robinfriedli.botify.command.commands.playlistmanagement;

import java.util.List;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class InsertCommand extends AddCommand {

    private String toAddString;
    private int targetIndex;

    public InsertCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() throws Exception {
        targetIndex = getArgumentValueWithType("at", Integer.class);
        toAddString = getCommandInput();

        super.doRun();
    }

    @Override
    protected void addToList(Playlist playlist, List<PlaylistItem> items) {
        if (!(targetIndex > 0 && targetIndex <= playlist.getSize())) {
            throw new InvalidCommandException(String.format("Invalid index: %d. Index is not within playlist of size %d. Use the add command to add items at the end of the list instead.", targetIndex, playlist.getSize()));
        }

        int actualIndex = targetIndex - 1;
        // move back all items that are at or after the target index
        List<PlaylistItem> itemsSorted = playlist.getItemsSorted(true);
        for (int i = actualIndex; i < itemsSorted.size(); i++) {
            PlaylistItem item = itemsSorted.get(i);
            item.setIndex(item.getIndex() + items.size());
        }
        // assign target indices
        for (int i = 0; i < items.size(); i++) {
            PlaylistItem item = items.get(i);
            item.setIndex(actualIndex + i);
        }

        super.addToList(playlist, items);
    }

    @Override
    protected String getToAddString() {
        return toAddString;
    }

}
