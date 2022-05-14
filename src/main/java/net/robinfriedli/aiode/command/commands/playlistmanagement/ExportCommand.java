package net.robinfriedli.aiode.command.commands.playlistmanagement;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.audio.youtube.HollowYouTubeVideo;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.util.SearchEngine;
import org.hibernate.Session;

public class ExportCommand extends AbstractCommand {

    public ExportCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Guild guild = getContext().getGuild();
        AudioQueue queue = Aiode.get().getAudioManager().getQueue(guild);

        if (queue.isEmpty()) {
            throw new InvalidCommandException("Queue is empty");
        }

        Playlist existingPlaylist = SearchEngine.searchLocalList(session, getCommandInput());
        if (existingPlaylist != null) {
            throw new InvalidCommandException("Playlist " + getCommandInput() + " already exists");
        }

        List<Playable> tracks = queue.getTracks();

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
