package net.robinfriedli.botify.tasks;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackBulkLoadingService;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.function.SpotifyInvoker;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;

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
                        String id = item.getAttribute("id").getValue();
                        int finalI = i;
                        spotifyBulkLoadingService.add(id, track -> {
                            String addedUserId = item.getAttribute("addedUserId").getValue();
                            User userById = guild.getJDA().getUserById(addedUserId);
                            Song song = new Song(track, userById, newPlaylist, session);
                            if (userById == null) {
                                song.setAddedUser(item.getAttribute("addedUser").getValue());
                                song.setAddedUserId(item.getAttribute("addedUserId").getValue());
                            }
                            itemsWithIndex.put(song, finalI);
                        });
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
}
