package net.robinfriedli.botify.tasks;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;

/**
 * task that updates the itemIndex property for {@link PlaylistItem}s when items are added to a playlist
 */
public class UpdatePlaylistItemIndicesTask implements PersistTask<Void> {

    private final Collection<Playlist> playlistsToUpdate;
    private final Comparator<PlaylistItem> sorter;

    public UpdatePlaylistItemIndicesTask(Collection<Playlist> playlistsToUpdate) {
        this(playlistsToUpdate, Comparator.comparing(PlaylistItem::getCreatedTimestamp));
    }

    public UpdatePlaylistItemIndicesTask(Collection<Playlist> playlistsToUpdate, Comparator<PlaylistItem> sorter) {
        this.playlistsToUpdate = playlistsToUpdate;
        this.sorter = sorter;
    }

    @Override
    public Void perform() {
        for (Playlist playlist : playlistsToUpdate) {
            List<PlaylistItem> items = playlist.getItems();
            List<PlaylistItem> itemsOrdered = items.stream()
                .filter(item -> item.getIndex() != null)
                .sorted(Comparator.comparing(PlaylistItem::getIndex))
                .collect(Collectors.toList());
            List<PlaylistItem> addedItems = items.stream()
                .filter(item -> item.getIndex() == null)
                .sorted(sorter)
                .collect(Collectors.toList());
            itemsOrdered.addAll(addedItems);

            for (int i = 0; i < itemsOrdered.size(); i++) {
                PlaylistItem item = itemsOrdered.get(i);
                if (item.getIndex() == null || item.getIndex() != i) {
                    item.setIndex(i);
                }
            }
        }

        return null;
    }
}
