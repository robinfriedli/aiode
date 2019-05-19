package net.robinfriedli.botify.listener;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

public class AlertEventListener extends ChainableInterceptor {

    private final MessageChannel channel;

    private List<Playlist> createdPlaylists = Lists.newArrayList();
    private List<Playlist> deletedPlaylists = Lists.newArrayList();
    private List<PlaylistItem> addedItems = Lists.newArrayList();
    private List<PlaylistItem> removedItems = Lists.newArrayList();
    private Preset createdPreset;
    private Preset deletedPreset;

    public AlertEventListener(MessageChannel channel, Interceptor next) {
        super(next);
        this.channel = channel;
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof Playlist) {
            Playlist playlist = (Playlist) entity;
            createdPlaylists.add(playlist);
        } else if (entity instanceof PlaylistItem) {
            addedItems.add((PlaylistItem) entity);
        } else if (entity instanceof Preset) {
            createdPreset = (Preset) entity;
        }
        return next().onSave(entity, id, state, propertyNames, types);
    }

    @Override
    public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof Playlist) {
            Playlist playlist = (Playlist) entity;
            deletedPlaylists.add(playlist);
        } else if (entity instanceof PlaylistItem) {
            removedItems.add((PlaylistItem) entity);
        } else if (entity instanceof Preset) {
            deletedPreset = (Preset) entity;
        }
        next().onDelete(entity, id, state, propertyNames, types);
    }

    @Override
    public void afterTransactionCompletion(Transaction tx) {
        MessageService messageService = new MessageService();
        if (!addedItems.isEmpty()) {
            if (addedItems.size() == 1) {
                PlaylistItem item = addedItems.get(0);
                if (!createdPlaylists.contains(item.getPlaylist())) {
                    messageService.sendSuccess("Added " + item.display() + " to " + item.getPlaylist().getName(), channel);
                }
            } else {
                Multimap<Playlist, PlaylistItem> playlistWithItems = HashMultimap.create();
                for (PlaylistItem playlistItem : addedItems) {
                    playlistWithItems.put(playlistItem.getPlaylist(), playlistItem);
                }
                for (Playlist playlist : playlistWithItems.keySet()) {
                    if (!createdPlaylists.contains(playlist)) {
                        messageService.sendSuccess("Added " + playlistWithItems.get(playlist).size() + " items to playlist " + playlist.getName(), channel);
                    }
                }
            }
        }

        if (!removedItems.isEmpty()) {
            if (removedItems.size() == 1) {
                PlaylistItem item = removedItems.get(0);
                if (!deletedPlaylists.contains(item.getPlaylist())) {
                    messageService.sendSuccess("Removed " + item.display() + " from " + item.getPlaylist().getName(), channel);
                }
            } else {
                Multimap<Playlist, PlaylistItem> playlistWithItems = HashMultimap.create();
                for (PlaylistItem playlistItem : removedItems) {
                    playlistWithItems.put(playlistItem.getPlaylist(), playlistItem);
                }
                for (Playlist playlist : playlistWithItems.keySet()) {
                    if (!deletedPlaylists.contains(playlist)) {
                        messageService.sendSuccess("Removed " + playlistWithItems.get(playlist).size() + " items from playlist " + playlist.getName(), channel);
                    }
                }
            }
        }

        if (!createdPlaylists.isEmpty()) {
            String s = createdPlaylists.size() > 1 ? "Created playlists: " : "Created playlist: ";
            messageService.sendSuccess(s + StringListImpl.create(createdPlaylists, Playlist::getName).toSeparatedString(", "), channel);
        }

        if (!deletedPlaylists.isEmpty()) {
            String s = deletedPlaylists.size() > 1 ? "Deleted playlists: " : "Deleted playlist: ";
            messageService.sendSuccess(s + StringListImpl.create(deletedPlaylists, Playlist::getName).toSeparatedString(", "), channel);
        }

        if (createdPreset != null) {
            messageService.sendSuccess("Saved preset " + createdPreset.getName(), channel);
        }

        if (deletedPreset != null) {
            messageService.sendSuccess("Deleted preset " + deletedPreset.getName(), channel);
        }

        addedItems.clear();
        removedItems.clear();
        createdPlaylists.clear();
        deletedPlaylists.clear();
        createdPreset = null;
        deletedPreset = null;

        next().afterTransactionCompletion(tx);
    }

}
