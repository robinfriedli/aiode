package net.robinfriedli.botify.command.commands;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.ClientQuestionEvent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class RemoveCommand extends AbstractCommand {

    public RemoveCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description,
            Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Pair<String, String> pair = splitInlineArgument("from");
        Session session = getContext().getSession();
        Playlist playlist = SearchEngine.searchLocalList(session, pair.getRight(), isPartitioned(), getContext().getGuild().getId());

        if (playlist == null) {
            throw new NoResultsFoundException("No local list found for " + pair.getRight());
        } else if (playlist.isEmpty()) {
            throw new InvalidCommandException("Playlist is empty");
        }

        if (argumentSet("index")) {
            StringList indices = StringListImpl.create(pair.getLeft(), "-");
            if (indices.size() == 1 || indices.size() == 2) {
                indices.applyForEach(String::trim);
                if (indices.size() == 1) {
                    int index = parse(indices.get(0));
                    checkIndex(index, playlist);
                    invoke(() -> session.delete(playlist.getItemsSorted().get(index - 1)));
                } else {
                    int start = parse(indices.get(0));
                    int end = parse(indices.get(1));
                    checkIndex(start, playlist);
                    checkIndex(end, playlist);
                    if (end <= start) {
                        throw new InvalidCommandException("End index needs to be greater than start.");
                    }

                    invoke(() -> playlist.getItemsSorted().subList(start - 1, end).forEach(session::delete));
                }
            } else {
                throw new InvalidCommandException("Expected one or two indices but found " + indices.size());
            }
        } else {
            List<PlaylistItem> playlistItems = SearchEngine.searchPlaylistItems(playlist, pair.getLeft());
            if (playlistItems.size() == 1) {
                invoke(() -> session.delete(playlistItems.get(0)));
            } else if (playlistItems.isEmpty()) {
                throw new NoResultsFoundException("No tracks found for " + pair.getLeft() + " on list " + pair.getRight());
            } else {
                ClientQuestionEvent question = new ClientQuestionEvent(this);
                for (int i = 0; i < playlistItems.size(); i++) {
                    PlaylistItem item = playlistItems.get(i);
                    question.mapOption(String.valueOf(i), item, item.display());
                }
                question.mapOption("all", playlistItems, "All of the above");
                askQuestion(question);
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
            throw new InvalidCommandException(String.format("Invalid index '%s'. Needs to in range 1 - %s", index, playlist.getSize()));
        }
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }

    @Override
    public void withUserResponse(Object option) {
        Session session = getContext().getSession();

        invoke(() -> {
            if (option instanceof List) {
                for (Object o : ((List) option)) {
                    session.delete(o);
                }
            } else {
                session.delete(option);
            }
        });

    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("index").setDescription("Remove items by their index. You can also provide an index" +
            "range like $botify remove 13-19 $from list. This includes starting and end index.");
        return argumentContribution;
    }

}
