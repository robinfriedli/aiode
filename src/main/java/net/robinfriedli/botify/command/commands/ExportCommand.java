package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.google.common.collect.Lists;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class ExportCommand extends AbstractCommand {

    public ExportCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, false, true, identifier,
            "Export the current tracks in the queue to a new local list like $botify export my list", Category.PLAYLIST_MANAGEMENT);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioQueue queue = getManager().getAudioManager().getQueue(guild);

        if (queue.isEmpty()) {
            throw new InvalidCommandException("Queue is empty");
        }

        Context persistContext = getPersistContext();

        XmlElement existingPlaylist = persistContext.query(and(
            instanceOf(Playlist.class),
            attribute("name").fuzzyIs(getCommandBody()))
        ).getOnlyResult();
        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandBody() + " already exists");
        }

        List<Playable> tracks = queue.getTracks();

        String playlistCountMax = PropertiesLoadingService.loadProperty("PLAYLIST_COUNT_MAX");
        if (playlistCountMax != null) {
            int maxPlaylists = Integer.parseInt(playlistCountMax);
            if (persistContext.getInstancesOf(Playlist.class).size() >= maxPlaylists) {
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
        List<XmlElement> playlistElems = Lists.newArrayList();

        for (Playable track : tracks) {
            Object delegate = track.delegate();
            if (delegate instanceof Track) {
                playlistElems.add(new Song((Track) delegate, createUser, persistContext));
            } else if (delegate instanceof YouTubeVideo) {
                YouTubeVideo youTubeVideo = (YouTubeVideo) delegate;
                if (!(youTubeVideo instanceof HollowYouTubeVideo && ((HollowYouTubeVideo) youTubeVideo).isCanceled())) {
                    playlistElems.add(new Video(youTubeVideo, createUser, persistContext));
                }
            }
        }

        persistContext.invoke(true, true,
            () -> new Playlist(getCommandBody(), createUser, playlistElems, persistContext).persist(),
            getContext().getChannel());
    }

    @Override
    public void onSuccess() {
        // success notification sent by AlertEventListener
    }

}
