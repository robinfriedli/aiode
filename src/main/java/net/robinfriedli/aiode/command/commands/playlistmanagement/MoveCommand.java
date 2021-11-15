package net.robinfriedli.aiode.command.commands.playlistmanagement;

import java.util.List;

import com.google.common.base.Splitter;
import net.dv8tion.jda.api.entities.Guild;
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

public class MoveCommand extends AbstractCommand {

    private final StringBuilder successMessageBuilder = new StringBuilder();

    public MoveCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        Session session = getContext().getSession();
        String playlistName = getArgumentValue("on");
        Playlist playlist = SearchEngine.searchLocalList(session, playlistName);

        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local playlist found for '%s'", playlistName));
        } else if (playlist.isEmpty()) {
            throw new InvalidCommandException("Playlist is empty");
        }

        String sourceIndex = getCommandInput();
        int targetIndex = getArgumentValueWithType("to", Integer.class);
        checkIndex(targetIndex, playlist);

        if (sourceIndex.contains("-")) {
            List<String> indices = Splitter.on("-").trimResults().omitEmptyStrings().splitToList(sourceIndex);

            if (indices.size() != 2) {
                throw new InvalidCommandException("Expected exactly two source indices but found " + indices.size());
            }

            int start = parse(indices.get(0));
            int end = parse(indices.get(1));
            checkIndex(start, playlist);
            checkIndex(end, playlist);

            if (start >= end) {
                throw new InvalidCommandException("End index needs to be greater than start");
            } else if (start == targetIndex) {
                throw new InvalidCommandException("Redundant move command. New and old index are the same.");
            } else if (targetIndex <= end && targetIndex >= start) {
                throw new InvalidCommandException("Target index is within to move range. Cannot move items.");
            }

            moveIndexRange(start - 1, end - 1, targetIndex - 1, playlist);
        } else {
            int index = parse(sourceIndex);
            checkIndex(index, playlist);

            if (index == targetIndex) {
                throw new InvalidCommandException("Redundant move command. New and old index are the same.");
            }

            moveSingleIndex(index - 1, targetIndex - 1, playlist);
        }
    }

    private void moveSingleIndex(int index, int targetIndex, Playlist playlist) {
        List<PlaylistItem> itemsSorted = playlist.getItemsSorted();
        PlaylistItem itemToMove = itemsSorted.get(index);
        invoke(() -> {
            if (index < targetIndex) {
                // item is being moved down
                // move up items that were after the moving item but are now before
                for (int i = index + 1; i <= targetIndex; i++) {
                    PlaylistItem item = itemsSorted.get(i);
                    item.setIndex(item.getIndex() - 1);
                }
            } else {
                // item is being moved up
                // move down items that were before the moving item but are now after
                for (int i = targetIndex; i < index; i++) {
                    PlaylistItem item = itemsSorted.get(i);
                    item.setIndex(item.getIndex() + 1);
                }
            }
            itemToMove.setIndex(targetIndex);
            successMessageBuilder.append("Moved item '").append(itemToMove.display()).append("' to index ").append(targetIndex + 1);
        });
    }

    private void moveIndexRange(int start, int end, int targetIndex, Playlist playlist) {
        List<PlaylistItem> itemsSorted = playlist.getItemsSorted();

        invoke(() -> {
            boolean movedDown = start < targetIndex;
            int range = end - start + 1;
            if (movedDown) {
                for (int i = start; i <= targetIndex; i++) {
                    PlaylistItem item = itemsSorted.get(i);
                    if (i <= end) {
                        // set items are within the defined start - end range to the desired index
                        // note that the end item ends up at the targetIndex because of how the items are moved up
                        // e.g moving 4 - 6 to 10
                        // before                   after
                        // 3. track 3               3. track 3
                        // 4. to move 1             4. track 7
                        // 5. to move 2             5. track 8
                        // 6. to move 3             6. track 9
                        // 7. track 7               7. track 10
                        // 8. track 8               8. to move 1
                        // 9. track 9               9. to move 2
                        // 10. track 10             10. to move 3
                        // 11. track 11             11. track 11
                        item.setIndex(targetIndex - (end - i));
                    } else {
                        item.setIndex(item.getIndex() - range);
                    }
                }
                String message = String.format("Moved items %d through %d behind item '%s'",
                    start + 1, end + 1, itemsSorted.get(targetIndex).display());
                successMessageBuilder.append(message);
            } else {
                // e.g. moving 14 - 16 to 10
                // before                           after
                // 9. track 9                       9. track 9
                // 10. track 10                     10. to move 1
                // 11. track 11                     11. to move 2
                // 12. track 12                     12. to move 3
                // 13. track 13                     13. track 10
                // 14. to move 1                    14. track 11
                // 15. to move 2                    15. track 12
                // 16. to move 3                    16. track 13
                // 17. track 17                     17. track 17
                for (int i = end; i >= targetIndex; i--) {
                    PlaylistItem item = itemsSorted.get(i);
                    if (i >= start) {
                        item.setIndex(targetIndex + i - start);
                    } else {
                        item.setIndex(item.getIndex() + range);
                    }
                }
                String message = String.format("Moved items %d through %d ahead of item '%s'",
                    start + 1, end + 1, itemsSorted.get(targetIndex).display());
                successMessageBuilder.append(message);
            }
        });
    }

    private int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(String.format("'%s' is not an integer", s));
        }
    }

    private void checkIndex(int index, Playlist playlist) {
        if (!(index > 0 && index <= playlist.getSize())) {
            throw new InvalidCommandException(String.format("Invalid index: %d. Expected value between 1 - %d", index, playlist.getSize()));
        }
    }

    @Override
    public void onSuccess() {
        if (successMessageBuilder.length() != 0) {
            sendSuccess(successMessageBuilder.toString());
        }
    }

}
