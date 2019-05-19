package net.robinfriedli.botify.command.commands;

import java.util.List;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableImpl;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.Playlist;
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
        AudioQueue queue = getManager().getAudioManager().getQueue(guild);

        if (queue.isEmpty()) {
            throw new InvalidCommandException("Queue is empty");
        }

        Playlist existingPlaylist = SearchEngine.searchLocalList(session, getCommandBody(), isPartitioned(), guild.getId());
        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandBody() + " already exists");
        }

        List<Playable> tracks = queue.getTracks();

        String playlistCountMax = PropertiesLoadingService.loadProperty("PLAYLIST_COUNT_MAX");
        if (playlistCountMax != null) {
            int maxPlaylists = Integer.parseInt(playlistCountMax);
            String query = "select count(*) from " + Playlist.class.getName();
            Long playlistCount = (Long) session.createQuery(isPartitioned() ? query + " where guild_id = '" + guild.getId() + "'" : query).uniqueResult();
            if (playlistCount >= maxPlaylists) {
                throw new InvalidCommandException("Maximum playlist count of " + maxPlaylists + " reached!");
            }
        }
        String playlistSizeMax = PropertiesLoadingService.loadProperty("PLAYLIST_SIZE_MAX");
        if (playlistSizeMax != null) {
            int maxSize = Integer.parseInt(playlistSizeMax);
            if (tracks.size() > maxSize) {
                throw new InvalidCommandException("List exceeds maximum size of " + maxSize + " items!");
            }
        }

        User createUser = getContext().getUser();
        Playlist playlist = new Playlist(getCommandBody(), createUser, guild);

        invoke(() -> {
            for (Playable track : tracks) {
                if (track instanceof PlayableImpl && ((PlayableImpl) track).delegate() instanceof HollowYouTubeVideo) {
                    HollowYouTubeVideo video = (HollowYouTubeVideo) ((PlayableImpl) track).delegate();
                    video.awaitCompletion();
                    if (video.isCanceled()) {
                        continue;
                    }
                }
                session.persist(track.export(playlist, getContext().getUser(), session));
            }

            session.persist(playlist);
        });
    }

    @Override
    public void onSuccess() {
        // success notification sent by AlertEventListener
    }

}
