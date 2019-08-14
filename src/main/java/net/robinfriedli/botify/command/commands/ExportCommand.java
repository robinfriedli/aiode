package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
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

        if (queue.isEmpty()) {
            throw new InvalidCommandException("Queue is empty");
        }

        Playlist existingPlaylist = SearchEngine.searchLocalList(session, getCommandInput(), isPartitioned(), guild.getId());
        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandInput() + " already exists");
        }

        List<Playable> tracks = queue.getTracks();

        String playlistCountMax = PropertiesLoadingService.loadProperty("PLAYLIST_COUNT_MAX");
        if (!Strings.isNullOrEmpty(playlistCountMax)) {
            int maxPlaylists = Integer.parseInt(playlistCountMax);
            String query = "select count(*) from " + Playlist.class.getName();
            Long playlistCount = (Long) session.createQuery(isPartitioned() ? query + " where guild_id = '" + guild.getId() + "'" : query).uniqueResult();
            if (playlistCount >= maxPlaylists) {
                throw new InvalidCommandException("Maximum playlist count of " + maxPlaylists + " reached!");
            }
        }
        String playlistSizeMax = PropertiesLoadingService.loadProperty("PLAYLIST_SIZE_MAX");
        if (!Strings.isNullOrEmpty(playlistSizeMax)) {
            int maxSize = Integer.parseInt(playlistSizeMax);
            if (tracks.size() > maxSize) {
                throw new InvalidCommandException("List exceeds maximum size of " + maxSize + " items!");
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
