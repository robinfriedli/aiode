package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.HollowYouTubeVideo;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableImpl;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class ExportCommand extends AbstractCommand {

    public ExportCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, false, false, true, identifier, description, Category.PLAYLIST_MANAGEMENT);
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
            if (track instanceof PlayableImpl && ((PlayableImpl) track).delegate() instanceof HollowYouTubeVideo) {
                HollowYouTubeVideo video = (HollowYouTubeVideo) ((PlayableImpl) track).delegate();
                video.awaitCompletion();
                if (video.isCanceled()) {
                    continue;
                }
            }
            playlistElems.add(track.export(persistContext, getContext().getUser()));
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
