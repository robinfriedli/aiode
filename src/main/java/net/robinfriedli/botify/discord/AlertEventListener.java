package net.robinfriedli.botify.discord;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.events.ElementCreatedEvent;
import net.robinfriedli.jxp.events.ElementDeletingEvent;
import net.robinfriedli.jxp.events.EventListener;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.persist.Transaction;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

public class AlertEventListener extends EventListener {

    @Override
    public void transactionApplied(Transaction transaction) {
        Context context = transaction.getContext();
        StringBuilder responseBuilder = new StringBuilder();
        List<Playlist> createdPlaylists = Lists.newArrayList();
        List<XmlElement> createdTracks = Lists.newArrayList();
        List<Playlist> removedPlaylists = Lists.newArrayList();
        Multimap<Playlist, XmlElement> removedTracks = HashMultimap.create();

        for (ElementCreatedEvent createdElement : transaction.getCreatedElements()) {
            XmlElement source = createdElement.getSource();
            if (source instanceof Playlist) {
                createdPlaylists.add((Playlist) source);
            } else if (source instanceof Song || source instanceof Video || source instanceof UrlTrack) {
                createdTracks.add(source);
            }
        }

        for (ElementDeletingEvent deletedElement : transaction.getDeletedElements()) {
            XmlElement source = deletedElement.getSource();
            if (source instanceof Playlist) {
                removedPlaylists.add((Playlist) source);
            } else if (source instanceof Song || source instanceof Video || source instanceof UrlTrack) {
                Playlist oldParent = (Playlist) deletedElement.getOldParent();
                removedTracks.put(oldParent, source);
            }
        }

        if (!createdPlaylists.isEmpty()) {
            StringList names = StringListImpl.create(createdPlaylists, playlist -> playlist.getAttribute("name").getValue());
            responseBuilder.append("Created playlists: ").append(names.toSeparatedString(", "))
                .append(System.lineSeparator());
        }

        if (!removedPlaylists.isEmpty()) {
            StringList names = StringListImpl.create(removedPlaylists, playlist -> playlist.getAttribute("name").getValue());
            responseBuilder.append("Deleted playlists: ").append(names.toSeparatedString(", ")).append(System.lineSeparator());
        }

        if (!createdTracks.isEmpty()) {
            Multimap<Playlist, XmlElement> trackWithPlaylist = HashMultimap.create();
            createdTracks.forEach(song -> trackWithPlaylist.put((Playlist) song.getParent(), song));

            for (Playlist playlist : trackWithPlaylist.keySet()) {
                Collection<XmlElement> tracks = trackWithPlaylist.get(playlist);
                if (tracks.size() == 1) {
                    XmlElement track = tracks.iterator().next();
                    String trackString = getTrackString(track);

                    responseBuilder.append("Added ").append(trackString).append(" to ").append(playlist.getAttribute("name").getValue())
                        .append(System.lineSeparator());
                } else {
                    responseBuilder.append("Added ").append(tracks.size()).append(" tracks to playlist ")
                        .append(playlist.getAttribute("name").getValue())
                        .append(System.lineSeparator());
                }
            }
        }

        if (!removedTracks.isEmpty()) {
            for (Playlist playlist : removedTracks.keySet()) {
                Collection<XmlElement> tracks = removedTracks.get(playlist);
                if (tracks.size() == 1) {
                    XmlElement track = tracks.iterator().next();
                    String trackString = getTrackString(track);

                    responseBuilder.append("Removed ").append(trackString).append(" from ").append(playlist.getAttribute("name").getValue())
                        .append(System.lineSeparator());
                } else {
                    responseBuilder.append("Removed ").append(tracks.size()).append(" tracks from playlist ")
                        .append(playlist.getAttribute("name").getValue())
                        .append(System.lineSeparator());
                }
            }
        }

        String response = responseBuilder.toString();
        if (!"".equals(response)) {
            Object envVar = context.getEnvVar();
            if (envVar instanceof MessageChannel) {
                ((MessageChannel) envVar).sendMessage(response).queue();
            } else {
                System.out.println(response);
            }
        }
    }

    private String getTrackString(XmlElement track) {
        if (track instanceof Song) {
            return track.getAttribute("name").getValue() + " by " + track.getAttribute("artists").getValue();
        } else {
            return track.getAttribute("title").getValue();
        }
    }

}
