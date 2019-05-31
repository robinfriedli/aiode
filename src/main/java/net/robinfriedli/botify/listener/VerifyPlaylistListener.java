package net.robinfriedli.botify.listener;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;

import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.tasks.UpdatePlaylistItemIndicesTask;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

/**
 * Interceptor that verifies and maintains the integrity of a playlist upon changes are made.
 *
 * Ensures that the item_index field always gets set and updated for items related to a playlist where the playlist items have changed.
 *
 * Also updates the collections on the playlist when an item gets deleted. When an item gets created this is done by the
 * constructor of the corresponding class or manually.
 *
 * Verifies the playlist's name when one is saved and removes subsequent spaces.
 */
public class VerifyPlaylistListener extends ChainableInterceptor {


    public VerifyPlaylistListener(Interceptor next, Logger logger) {
        super(next, logger);
    }

    @Override
    public void onSaveChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) throws Exception {
        if (entity instanceof Playlist) {
            Playlist playlist = (Playlist) entity;
            String newName = Playlist.sanatizeName(playlist.getName());
            if (!newName.equals(playlist.getName())) {
                playlist.setName(newName);
                for (int i = 0; i < propertyNames.length; i++) {
                    if ("name".equals(propertyNames[i])) {
                        state[i] = newName;
                    }
                }
            }
        }
    }

    @Override
    public void onDeleteChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof PlaylistItem) {
            PlaylistItem playlistItem = (PlaylistItem) entity;
            Playlist playlist = playlistItem.getPlaylist();
            if (playlistItem instanceof Song) {
                playlist.getSongs().remove(playlistItem);
            } else if (playlistItem instanceof Video) {
                playlist.getVideos().remove(playlistItem);
            } else if (playlistItem instanceof UrlTrack) {
                playlist.getUrlTracks().remove(playlistItem);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void preFlushChained(Iterator entities) {
        Iterable<Object> iterable = () -> entities;
        Set<Playlist> playlistsToUpdate = StreamSupport.stream(iterable.spliterator(), false)
            .filter(entity -> entity instanceof PlaylistItem)
            .map(entity -> ((PlaylistItem) entity).getPlaylist())
            .collect(Collectors.toSet());

        if (!playlistsToUpdate.isEmpty()) {
            UpdatePlaylistItemIndicesTask task = new UpdatePlaylistItemIndicesTask(playlistsToUpdate);
            task.perform();
        }
    }

}

