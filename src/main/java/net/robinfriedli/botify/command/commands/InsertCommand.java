package net.robinfriedli.botify.command.commands;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class InsertCommand extends AddCommand {

    private String toAddString;
    private int targetIndex;

    public InsertCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, identifier, description);
    }

    @Override
    public void doRun() {
        Pair<String, String> toAddStringWithIndex = splitInlineArgument("at");
        toAddString = toAddStringWithIndex.getLeft();

        try {
            targetIndex = Integer.parseInt(toAddStringWithIndex.getRight());
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(String.format("'%s' is not an integer", toAddStringWithIndex.getRight()));
        }

        super.doRun();
    }

    @Override
    protected void addToList(Playlist playlist, List<PlaylistItem> items) {
        if (!playlist.isEmpty()) {
            if (!(targetIndex > 0 && targetIndex <= playlist.getSize())) {
                throw new InvalidCommandException(String.format("Invalid index: %s. Expected value between 1 - %s", targetIndex, playlist.getSize()));
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

        }
        super.addToList(playlist, items);
    }

    @Override
    protected String getToAddString() {
        return toAddString;
    }
}
