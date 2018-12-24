package net.robinfriedli.botify.command.commands;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ClientQuestionEvent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.jxp.api.XmlAttribute;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

public class RemoveCommand extends AbstractCommand {

    public RemoveCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, true,
            "Remove an item from a local playlist. Put either the full title of the video or the spotify " +
                "track name including 'by' plus the artist.");
    }

    @Override
    public void doRun() {
        Pair<String, String> pair = splitInlineArgument("from");
        Context persistContext = getPersistContext();
        Playlist playlist = SearchEngine.searchLocalList(persistContext, pair.getRight());

        if (playlist == null) {
            throw new NoResultsFoundException("No local list found for " + pair.getRight());
        }

        List<XmlElement> playlistItems = SearchEngine.getPlaylistItems(playlist, pair.getLeft());
        if (playlistItems.size() == 1) {
            persistContext.invoke(true, true, () -> playlistItems.get(0).delete(), getContext().getChannel());
        } else if (playlistItems.isEmpty()) {
            throw new NoResultsFoundException("No tracks found for " + pair.getLeft() + " on list " + pair.getRight());
        } else {
            ClientQuestionEvent question = new ClientQuestionEvent(this);
            for (int i = 0; i < playlistItems.size(); i++) {
                XmlElement item = playlistItems.get(i);
                XmlAttribute displayAttribute = item instanceof Song ? item.getAttribute("name") : item.getAttribute("title");
                question.mapOption(String.valueOf(i), item, displayAttribute.getValue());
            }
            question.mapOption("all", playlistItems, "All of the above");
            askQuestion(question);
        }
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }

    @SuppressWarnings("unchecked")
    @Override
    public void withUserResponse(Object option) {
        Context persistContext = getPersistContext();

        if (option instanceof List) {
            persistContext.invoke(true, true, () -> ((List<XmlElement>) option).forEach(XmlElement::delete), getContext().getChannel());
        } else {
            persistContext.invoke(true, true, ((XmlElement) option)::delete, getContext().getChannel());
        }
    }

}
