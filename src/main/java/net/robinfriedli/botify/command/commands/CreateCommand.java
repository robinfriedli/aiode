package net.robinfriedli.botify.command.commands;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class CreateCommand extends AbstractCommand {

    public CreateCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, false, false, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
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

        String playlistCountMax = PropertiesLoadingService.loadProperty("PLAYLIST_COUNT_MAX");
        if (playlistCountMax != null) {
            int maxPlaylists = Integer.parseInt(playlistCountMax);
            if (persistContext.getInstancesOf(Playlist.class).size() >= maxPlaylists) {
                throw new InvalidCommandException("Maximum playlist count of " + maxPlaylists + " reached!");
            }
        }

        Playlist playlist = new Playlist(getCommandBody(), getContext().getUser(), Lists.newArrayList(), persistContext);
        persistContext.invoke(true, true, playlist::persist, getContext().getChannel());
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }
}
