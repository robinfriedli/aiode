package net.robinfriedli.aiode.entities;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.api.client.util.Sets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackBulkLoadingService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import se.michaelthelin.spotify.SpotifyApi;

import static net.robinfriedli.aiode.audio.spotify.SpotifyTrackBulkLoadingService.*;
import static net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind.*;

@Entity
@Table(name = "playlist", indexes = {
    @Index(name = "playlist_guild_id_idx", columnList = "guild_id")
})
public class Playlist implements Serializable, SanitizedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "name")
    @Size(min = 1, max = 30, message = "Invalid length of playlist name. Needs to be between 1 and 30.")
    private String name;
    @Column(name = "created_user")
    private String createdUser;
    @Column(name = "created_user_id")
    private String createdUserId;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @OneToMany(mappedBy = "playlist")
    private Set<Song> songs = Sets.newHashSet();
    @OneToMany(mappedBy = "playlist")
    private Set<Video> videos = Sets.newHashSet();
    @OneToMany(mappedBy = "playlist")
    private Set<UrlTrack> urlTracks = Sets.newHashSet();
    @OneToMany(mappedBy = "playlist")
    private Set<Episode> episodes = Sets.newHashSet();

    public Playlist() {
    }

    public Playlist(String name, User user, Guild guild) {
        setName(name);
        createdUser = user.getName();
        createdUserId = user.getId();
        this.guild = guild.getName();
        this.guildId = guild.getId();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getDuration() {
        return getItems().stream().mapToLong(PlaylistItem::getDuration).sum();
    }

    public int getSize() {
        return songs.size() + videos.size() + urlTracks.size() + episodes.size();
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
    }

    public String getCreatedUserId() {
        return createdUserId;
    }

    public void setCreatedUserId(String createdUserId) {
        this.createdUserId = createdUserId;
    }

    public String getGuild() {
        return guild;
    }

    public void setGuild(String guild) {
        this.guild = guild;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public List<PlaylistItem> getItems() {
        return Stream.of(getSongs(), getVideos(), getUrlTracks(), getEpisodes())
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    public List<PlaylistItem> getItemsSorted() {
        return getItemsSorted(false);
    }

    public List<PlaylistItem> getItemsSorted(boolean ignoreNullIndex) {
        return Stream.of(getSongs(), getVideos(), getUrlTracks(), getEpisodes())
            .flatMap(Collection::stream)
            .filter(item -> {
                if (ignoreNullIndex) {
                    return item.getIndex() != null;
                } else {
                    return true;
                }
            })
            .sorted(Comparator.comparing(PlaylistItem::getIndex))
            .collect(Collectors.toList());
    }

    public Stream<PlaylistItem> stream() {
        return Stream.of(getSongs(), getVideos(), getUrlTracks(), getEpisodes()).flatMap(Collection::stream);
    }

    /**
     * Returns the items in this playlist as objects supported by the {@link PlayableFactory} class. Note that getting the
     * Spotify track for a Song requires this method to be invoked with client credentials
     */
    public List<Object> getTracks(SpotifyApi spotifyApi) {
        List<PlaylistItem> playlistItems = getItemsSorted();
        SpotifyTrackBulkLoadingService service = new SpotifyTrackBulkLoadingService(spotifyApi);

        Map<Object, Integer> itemsWithIndex = new HashMap<>();
        for (int i = 0; i < playlistItems.size(); i++) {
            PlaylistItem item = playlistItems.get(i);
            if (item instanceof Song) {
                String id = ((Song) item).getId();
                int finalI = i;
                service.add(createItem(id, TRACK), track -> itemsWithIndex.put(track, finalI));
            } else if (item instanceof Episode) {
                String id = ((Episode) item).getId();
                int finalI = i;
                service.add(createItem(id, EPISODE), track -> itemsWithIndex.put(track, finalI));
            } else if (item instanceof Video video) {
                YouTubeVideo youtubeVideo = video.asYouTubeVideo();
                itemsWithIndex.put(youtubeVideo, i);
                String spotifyId = video.getRedirectedSpotifyId();
                if (!Strings.isNullOrEmpty(spotifyId)) {
                    SpotifyItemKind kindEntity = video.getRedirectedSpotifyKind();
                    SpotifyTrackKind kind = kindEntity != null ? kindEntity.asEnum() : TRACK;
                    service.add(createItem(spotifyId, kind), youtubeVideo::setRedirectedSpotifyTrack);
                }
            } else if (item instanceof UrlTrack) {
                itemsWithIndex.put(item, i);
            }
        }

        service.perform();
        return itemsWithIndex.keySet().stream().sorted(Comparator.comparing(itemsWithIndex::get)).collect(Collectors.toList());
    }

    /**
     * returns all Songs as Spotify tracks including all videos that are redirected Spotify tracks i.e. the attribute
     * redirectedSpotifyId is set. Mind that this method has to be invoked with client credentials
     */
    public List<SpotifyTrack> asTrackList(SpotifyApi spotifyApi) {
        SpotifyTrackBulkLoadingService service = new SpotifyTrackBulkLoadingService(spotifyApi);
        List<SpotifyTrack> tracks = Lists.newArrayList();
        for (PlaylistItem item : getItemsSorted()) {
            if (item instanceof Song) {
                String id = ((Song) item).getId();
                service.add(createItem(id, TRACK), tracks::add);
            } else if (item instanceof Episode) {
                String id = ((Episode) item).getId();
                service.add(createItem(id, EPISODE), tracks::add);
            } else if (item instanceof Video && ((Video) item).getRedirectedSpotifyId() != null) {
                Video video = (Video) item;
                String redirectedSpotifyId = video.getRedirectedSpotifyId();
                SpotifyItemKind kindEntity = video.getRedirectedSpotifyKind();
                SpotifyTrackKind kind = kindEntity != null ? kindEntity.asEnum() : TRACK;
                service.add(createItem(redirectedSpotifyId, kind), tracks::add);
            }
        }

        service.perform();
        return tracks;
    }

    public Set<Song> getSongs() {
        return songs;
    }

    public void setSongs(Set<Song> songs) {
        this.songs = songs;
    }

    public Set<Video> getVideos() {
        return videos;
    }

    public void setVideos(Set<Video> videos) {
        this.videos = videos;
    }

    public Set<UrlTrack> getUrlTracks() {
        return urlTracks;
    }

    public void setUrlTracks(Set<UrlTrack> urlTracks) {
        this.urlTracks = urlTracks;
    }

    public Set<Episode> getEpisodes() {
        return episodes;
    }

    public void setEpisodes(Set<Episode> episodes) {
        this.episodes = episodes;
    }

    public boolean isEmpty() {
        return songs.isEmpty() && videos.isEmpty() && urlTracks.isEmpty() && episodes.isEmpty();
    }

    @Override
    public int getMaxEntityCount(SpringPropertiesConfig springPropertiesConfig) {
        Integer playlistCountMax = springPropertiesConfig.getApplicationProperty(Integer.class, "aiode.preferences.playlist_count_max");
        return Objects.requireNonNullElse(playlistCountMax, 0);
    }

    @Override
    public String getIdentifierPropertyName() {
        return "name";
    }

    @Override
    public String getIdentifier() {
        return getName();
    }

    @Override
    public void setSanitizedIdentifier(String sanitizedIdentifier) {
        setName(sanitizedIdentifier);
    }
}
