package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.google.common.collect.Lists;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.YouTubeVideo;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class ExportCommand extends AbstractCommand {

    public ExportCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, true,
            "Export the current tracks in the queue to a new local list like $botify export my list");
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
        User createUser = getContext().getUser();
        List<XmlElement> playlistElems = Lists.newArrayList();

        for (Playable track : tracks) {
            Object delegate = track.delegate();
            if (delegate instanceof Track) {
                playlistElems.add(new Song((Track) delegate, createUser, persistContext));
            } else if (delegate instanceof YouTubeVideo) {
                YouTubeVideo youTubeVideo = (YouTubeVideo) delegate;
                playlistElems.add(new Video(youTubeVideo, createUser, youTubeVideo.getRedirectedSpotifyTrack(), persistContext));
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
