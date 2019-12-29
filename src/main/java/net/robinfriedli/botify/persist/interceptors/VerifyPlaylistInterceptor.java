package net.robinfriedli.botify.persist.interceptors;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.persistence.FlushModeType;

import org.slf4j.Logger;

import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.persist.tasks.UpdatePlaylistItemIndicesTask;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

/**
 * Interceptor that verifies and maintains the integrity of a playlist upon changes are made.
 * <p>
 * Ensures that the item_index field always gets set and updated for items related to a playlist where the playlist items have changed.
 * Sets the ordinal field on the PlaylistItem for newly created items for sorting.
 * <p>
 * Also updates the collections on the playlist when an item gets deleted. When an item gets created this is done by the
 * constructor of the corresponding class or manually.
 * <p>
 * Verifies the playlist's name when one is saved and removes subsequent spaces.
 */
public class VerifyPlaylistInterceptor extends ChainableInterceptor {

    private final QueryBuilderFactory queryBuilderFactory;
    private final SpringPropertiesConfig springPropertiesConfig;

    private boolean createdPlaylist;
    private int ordinal;

    public VerifyPlaylistInterceptor(Interceptor next, Logger logger, QueryBuilderFactory queryBuilderFactory, SpringPropertiesConfig springPropertiesConfig) {
        super(next, logger);
        this.queryBuilderFactory = queryBuilderFactory;
        this.springPropertiesConfig = springPropertiesConfig;
    }

    @Override
    public void onSaveChained(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof Playlist) {
            createdPlaylist = true;
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
        } else if (entity instanceof PlaylistItem) {
            ((PlaylistItem) entity).setOrdinal(ordinal);
            ++ordinal;
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

    @Override
    public void preFlush(Iterator entities) {
        if (createdPlaylist) {
            checkPlaylistCount();
        }

        @SuppressWarnings("unchecked")
        Iterable<Object> iterable = () -> entities;
        Set<Playlist> playlistsToUpdate = StreamSupport.stream(iterable.spliterator(), false)
            .filter(entity -> entity instanceof PlaylistItem)
            .map(entity -> ((PlaylistItem) entity).getPlaylist())
            .collect(Collectors.toSet());

        if (!playlistsToUpdate.isEmpty()) {
            playlistsToUpdate.forEach(this::checkPlaylistSize);
            UpdatePlaylistItemIndicesTask task = new UpdatePlaylistItemIndicesTask(playlistsToUpdate, Comparator.comparing(PlaylistItem::getOrdinal));
            task.perform();
        }

        super.preFlush(entities);
    }

    @Override
    public void afterTransactionCompletionChained(Transaction tx) {
        ordinal = 0;
        createdPlaylist = false;
    }

    private void checkPlaylistCount() {
        StaticSessionProvider.invokeWithSession(session -> {
            Long playlistCount = queryBuilderFactory.select(Playlist.class, ((from, cb) -> cb.count(from.get("pk"))), Long.class)
                .build(session)
                .setFlushMode(FlushModeType.COMMIT)
                .uniqueResult();
            Integer playlistCountMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.playlist_count_max");
            if (playlistCountMax != null && playlistCount.compareTo(playlistCountMax.longValue()) > 0) {
                throw new InvalidCommandException("Maximum playlist count of " + playlistCountMax + " reached!");
            }
        });
    }

    private void checkPlaylistSize(Playlist playlist) {
        Integer playlistSizeMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.playlist_size_max");
        if (playlistSizeMax != null) {
            if (playlist.getSize() > playlistSizeMax) {
                throw new InvalidCommandException("List exceeds maximum size of " + playlistSizeMax + " items!");
            }
        }
    }

}

