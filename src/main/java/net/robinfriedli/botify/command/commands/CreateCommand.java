package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class CreateCommand extends AbstractCommand {

    public CreateCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Playlist existingPlaylist = SearchEngine.searchLocalList(session, getCommandBody(), isPartitioned(), getContext().getGuild().getId());

        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandBody() + " already exists");
        }

        String playlistCountMax = PropertiesLoadingService.loadProperty("PLAYLIST_COUNT_MAX");
        if (playlistCountMax != null) {
            int maxPlaylists = Integer.parseInt(playlistCountMax);
            String query = "select count(*) from " + Playlist.class.getName();
            Long playlistCount = (Long) session.createQuery(isPartitioned() ? query + " where guild_id = '" + getContext().getGuild().getId() + "'" : query).uniqueResult();
            if (playlistCount >= maxPlaylists) {
                throw new InvalidCommandException("Maximum playlist count of " + maxPlaylists + " reached!");
            }
        }

        Playlist playlist = new Playlist(getCommandBody(), getContext().getUser(), getContext().getGuild());
        invoke(() -> session.persist(playlist));
    }

    @Override
    public void onSuccess() {
        // notification sent by AlertEventListener
    }
}
