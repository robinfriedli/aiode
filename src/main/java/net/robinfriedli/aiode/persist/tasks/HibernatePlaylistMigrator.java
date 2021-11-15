package net.robinfriedli.aiode.persist.tasks;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackBulkLoadingService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.aiode.entities.Episode;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.Song;
import net.robinfriedli.aiode.entities.UrlTrack;
import net.robinfriedli.aiode.entities.Video;
import net.robinfriedli.aiode.function.SpotifyInvoker;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import se.michaelthelin.spotify.SpotifyApi;

import static net.robinfriedli.aiode.audio.spotify.SpotifyTrackBulkLoadingService.*;
import static net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind.*;
import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Service that creates {@link Playlist} entities based on legacy XML playlists
 */
public class HibernatePlaylistMigrator implements PersistTask<Map<Playlist, List<PlaylistItem>>> {

    private final Context context;
    private final Guild guild;
    private final SpotifyApi spotifyApi;
    private final Session session;

    public HibernatePlaylistMigrator(Context context, Guild guild, SpotifyApi spotifyApi, Session session) {
        this.context = context;
        this.guild = guild;
        this.spotifyApi = spotifyApi;
        this.session = session;
    }

    @Override
    public Map<Playlist, List<PlaylistItem>> perform() throws Exception {
        ShardManager shardManager = Aiode.get().getShardManager();
        SpotifyTrackBulkLoadingService spotifyBulkLoadingService = new SpotifyTrackBulkLoadingService(spotifyApi);
        List<XmlElement> playlists = context.query(tagName("playlist")).collect();
        Map<Playlist, List<PlaylistItem>> playlistMap = new HashMap<>();
        for (XmlElement playlist : playlists) {
            Playlist newPlaylist = new Playlist();
            newPlaylist.setName(playlist.getAttribute("name").getValue());
            newPlaylist.setCreatedUser(playlist.getAttribute("createdUser").getValue());
            newPlaylist.setCreatedUserId(playlist.getAttribute("createdUserId").getValue());
            newPlaylist.setGuild(guild.getName());
            newPlaylist.setGuildId(guild.getId());

            List<XmlElement> items = playlist.getSubElements();
            Map<PlaylistItem, Integer> itemsWithIndex = new HashMap<>();
            for (int i = 0; i < items.size(); i++) {
                XmlElement item = items.get(i);
                switch (item.getTagName()) {
                    case "song":
                        loadSpotifyItem(item, i, spotifyBulkLoadingService, shardManager, newPlaylist, itemsWithIndex, TRACK);
                        break;
                    case "episode":
                        loadSpotifyItem(item, i, spotifyBulkLoadingService, shardManager, newPlaylist, itemsWithIndex, EPISODE);
                        break;
                    case "video":
                        Video video = new Video();
                        video.setId(item.getAttribute("id").getValue());
                        video.setTitle(item.getAttribute("title").getValue());
                        if (item.hasAttribute("redirectedSpotifyId")) {
                            video.setRedirectedSpotifyId(item.getAttribute("redirectedSpotifyId").getValue());
                        }
                        if (item.hasAttribute("spotifyTrackName")) {
                            video.setSpotifyTrackName(item.getAttribute("spotifyTrackName").getValue());
                        }
                        video.setPlaylist(newPlaylist);
                        video.setDuration(item.getAttribute("duration").getLong());
                        video.setAddedUser(item.getAttribute("addedUser").getValue());
                        video.setAddedUserId(item.getAttribute("addedUserId").getValue());
                        newPlaylist.getVideos().add(video);
                        itemsWithIndex.put(video, i);
                        break;
                    case "urlTrack":
                        UrlTrack urlTrack = new UrlTrack();
                        urlTrack.setUrl(item.getAttribute("url").getValue());
                        urlTrack.setTitle(item.getAttribute("title").getValue());
                        urlTrack.setDuration(item.getAttribute("duration").getLong());
                        urlTrack.setAddedUser(item.getAttribute("addedUser").getValue());
                        urlTrack.setAddedUserId(item.getAttribute("addedUserId").getValue());
                        urlTrack.setPlaylist(newPlaylist);
                        newPlaylist.getUrlTracks().add(urlTrack);
                        itemsWithIndex.put(urlTrack, i);
                        break;
                }
            }

            SpotifyInvoker.create(spotifyApi).invoke(() -> {
                spotifyBulkLoadingService.perform();
                return null;
            });

            List<PlaylistItem> playlistItems = itemsWithIndex.keySet().stream().sorted(Comparator.comparing(itemsWithIndex::get)).collect(Collectors.toList());
            playlistMap.put(newPlaylist, playlistItems);
        }

        return playlistMap;
    }

    private void loadSpotifyItem(XmlElement item,
                                 int index,
                                 SpotifyTrackBulkLoadingService spotifyBulkLoadingService,
                                 ShardManager shardManager,
                                 Playlist newPlaylist,
                                 Map<PlaylistItem, Integer> itemsWithIndex,
                                 SpotifyTrackKind kind) {
        String id = item.getAttribute("id").getValue();
        spotifyBulkLoadingService.add(createItem(id, kind), spotifyTrack -> {
            String addedUser = item.getAttribute("addedUser").getValue();
            String addedUserId = item.getAttribute("addedUserId").getValue();
            PlaylistItem playlistItem = spotifyTrack.exhaustiveMatch(
                track -> new Song(track, addedUser, addedUserId, newPlaylist, session),
                episode -> new Episode(episode, addedUser, addedUserId, newPlaylist)
            );

            playlistItem.setAddedUser(item.getAttribute("addedUser").getValue());
            playlistItem.setAddedUserId(item.getAttribute("addedUserId").getValue());
            itemsWithIndex.put(playlistItem, index);
        });
    }

}
