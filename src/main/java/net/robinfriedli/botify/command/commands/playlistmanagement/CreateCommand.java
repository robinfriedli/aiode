package net.robinfriedli.botify.command.commands.playlistmanagement;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class CreateCommand extends AbstractCommand {

    public CreateCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        SpringPropertiesConfig springPropertiesConfig = Botify.get().getSpringPropertiesConfig();
        Playlist existingPlaylist = SearchEngine.searchLocalList(session, getCommandInput(), isPartitioned(), getContext().getGuild().getId());

        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandInput() + " already exists");
        }

        Integer playlistCountMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.playlist_count_max");
        if (playlistCountMax != null) {
            String query = "select count(*) from " + Playlist.class.getName();
            Long playlistCount = (Long) session.createQuery(isPartitioned() ? query + " where guild_id = '" + getContext().getGuild().getId() + "'" : query).uniqueResult();
            if (playlistCount >= playlistCountMax) {
                throw new InvalidCommandException("Maximum playlist count of " + playlistCountMax + " reached!");
            }
        }

        Playlist playlist = new Playlist(getCommandInput(), getContext().getUser(), getContext().getGuild());
        invoke(() -> session.persist(playlist));
    }

    @Override
    public void onSuccess() {
        // notification sent by interceptor
    }
}
