package net.robinfriedli.aiode.command.commands.playlistmanagement;

import java.util.Collection;
import java.util.List;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.exceptions.NoResultsFoundException;
import net.robinfriedli.aiode.util.SearchEngine;
import net.robinfriedli.stringlist.StringList;
import org.hibernate.Session;

import static java.lang.String.*;

public class RemoveCommand extends AbstractCommand {

    public RemoveCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        String playlistName = getArgumentValue("from");
        Playlist playlist = SearchEngine.searchLocalList(session, playlistName);

        if (playlist == null) {
            throw new NoResultsFoundException(String.format("No local list found for '%s'", playlistName));
        } else if (playlist.isEmpty()) {
            throw new InvalidCommandException("Playlist is empty");
        }

        if (argumentSet("index")) {
            StringList indices = StringList.createWithRegex(getCommandInput(), "-");
            if (indices.size() == 1 || indices.size() == 2) {
                indices.applyForEach(String::trim);
                if (indices.size() == 1) {
                    int index = parse(indices.get(0));
                    checkIndex(index, playlist);
                    invoke(() -> session.remove(playlist.getItemsSorted().get(index - 1)));
                } else {
                    int start = parse(indices.get(0));
                    int end = parse(indices.get(1));
                    checkIndex(start, playlist);
                    checkIndex(end, playlist);
                    if (end <= start) {
                        throw new InvalidCommandException("End index needs to be greater than start.");
                    }

                    invoke(() -> playlist.getItemsSorted().subList(start - 1, end).forEach(session::remove));
                }
            } else {
                throw new InvalidCommandException("Expected one or two indices but found " + indices.size());
            }
        } else {
            List<PlaylistItem> playlistItems = SearchEngine.searchPlaylistItems(playlist, getCommandInput());
            if (playlistItems.size() == 1) {
                invoke(() -> session.remove(playlistItems.get(0)));
            } else if (playlistItems.isEmpty()) {
                throw new NoResultsFoundException(String.format("No tracks found for '%s' on list '%s'", getCommandInput(), playlistName));
            } else {
                askQuestion(
                    playlistItems,
                    PlaylistItem::display,
                    item -> valueOf(item.getIndex() + 1)
                );
            }
        }
    }

    private int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(s + " is not an integer");
        }
    }

    private void checkIndex(int index, Playlist playlist) {
        if (!(index > 0 && index <= playlist.getSize())) {
            throw new InvalidCommandException(format("Invalid index '%d'. Needs to in range 1 - %d", index, playlist.getSize()));
        }
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }

    @Override
    public void withUserResponse(Object option) {
        consumeSession(session -> {
            if (option instanceof Collection playlistItems) {
                for (Object o : playlistItems) {
                    PlaylistItem playlistItem = (PlaylistItem) o;
                    PlaylistItem reloadedItem = session.getReference(playlistItem.getClass(), playlistItem.getPk());
                    session.remove(reloadedItem);
                }
            } else {
                PlaylistItem playlistItem = (PlaylistItem) option;
                PlaylistItem reloadedItem = session.getReference(playlistItem.getClass(), playlistItem.getPk());
                session.remove(reloadedItem);
            }
        });

    }

}
