package net.robinfriedli.botify.command.commands.playlistmanagement;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class ExportCommand extends AbstractCommand {

    public ExportCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Guild guild = getContext().getGuild();
        AudioQueue queue = Botify.get().getAudioManager().getQueue(guild);
        SpringPropertiesConfig springPropertiesConfig = Botify.get().getSpringPropertiesConfig();

        if (queue.isEmpty()) {
            throw new InvalidCommandException("Queue is empty");
        }

        Playlist existingPlaylist = SearchEngine.searchLocalList(session, getCommandInput(), isPartitioned(), guild.getId());
        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandInput() + " already exists");
        }

        List<Playable> tracks = queue.getTracks();

        Integer playlistCountMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.playlist_count_max");
        if (playlistCountMax != null) {
            String query = "select count(*) from " + Playlist.class.getName();
            Long playlistCount = (Long) session.createQuery(isPartitioned() ? query + " where guild_id = '" + guild.getId() + "'" : query).uniqueResult();
            if (playlistCount >= playlistCountMax) {
                throw new InvalidCommandException("Maximum playlist count of " + playlistCountMax + " reached!");
            }
        }
        Integer playlistSizeMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.playlist_size_max");
        if (playlistSizeMax != null) {
            if (tracks.size() > playlistSizeMax) {
                throw new InvalidCommandException("List exceeds maximum size of " + playlistSizeMax + " items!");
            }
        }

        User createUser = getContext().getUser();
        Playlist playlist = new Playlist(getCommandInput(), createUser, guild);

        invoke(() -> {
            session.persist(playlist);

            for (Playable track : tracks) {
                if (track instanceof HollowYouTubeVideo) {
                    HollowYouTubeVideo video = (HollowYouTubeVideo) track;
                    video.awaitCompletion();
                    if (video.isCanceled()) {
                        continue;
                    }
                }
                PlaylistItem export = track.export(playlist, getContext().getUser(), session);
                export.add();
                session.persist(export);
            }
        });
    }

    @Override
    public void onSuccess() {
        // success notification sent by interceptor
    }

}
