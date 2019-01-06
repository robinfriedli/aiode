package net.robinfriedli.botify.command.commands;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class CreateCommand extends AbstractCommand {

    public CreateCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, false, true, identifier,
            "Create an emtpy local playlist with the given name like $botify create my list", Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Context persistContext = getPersistContext();
        XmlElement existingPlaylist = persistContext.query(
            and(
                instanceOf(Playlist.class),
                attribute("name").fuzzyIs(getCommandBody())
            )
        ).getOnlyResult();

        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandBody() + " already exists");
        }

        Playlist playlist = new Playlist(getCommandBody(), getContext().getUser(), Lists.newArrayList(), persistContext);
        persistContext.invoke(true, true, playlist::persist, getContext().getChannel());
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }
}
