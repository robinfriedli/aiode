package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.google.api.client.util.Sets;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.spotify.PlayableTrackWrapper;
import net.robinfriedli.botify.audio.spotify.SpotifyTrack;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import org.hibernate.Session;

@Entity
@Table(name = "playback_history")
public class PlaybackHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    @Column(name = "title")
    private String title;
    @Column(name = "track_id")
    private String trackId;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @ManyToMany
    @JoinTable(name = "playback_history_artist", joinColumns = {@JoinColumn(name = "playback_history_pk")}, inverseJoinColumns = {@JoinColumn(name = "artist_pk")})
    private Set<Artist> artists = Sets.newHashSet();
    @OneToMany(mappedBy = "playbackHistory")
    private Set<UserPlaybackHistory> userPlaybackHistories = Sets.newHashSet();
    @ManyToOne
    @JoinColumn(name = "fk_source", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "playback_history_fk_source_fkey"))
    private PlaybackHistorySource source;
    @ManyToOne
    @JoinColumn(name = "fk_spotify_item_kind", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "playback_history_fk_spotify_item_kind_fkey"))
    private SpotifyItemKind spotifyItemKind;

    // explicit declaration of fk columns, required in order to simply read fk column without joining relation
    //
    // this enables using from.get("fkSource") instead of root.join("source").get("pk") if you simply want to read the fk
    // and avoid an unnecessary joins that would require the pk of the relation and cause issues e.g. for GROUP BY queries
    @SuppressWarnings("unused")
    @Column(name = "fk_source", insertable = false, updatable = false)
    private long fkSource;
    @SuppressWarnings("unused")
    @Column(name = "fk_spotify_item_kind", insertable = false, updatable = false)
    private long fkSpotifyItemKind;

    public PlaybackHistory() {
    }

    public PlaybackHistory(LocalDateTime timestamp, Playable playable, Guild guild, Session session) {
        try {
            this.timestamp = timestamp;
            SpotifyTrack spotifyTrack = null;
            if (playable instanceof PlayableTrackWrapper) {
                spotifyTrack = ((PlayableTrackWrapper) playable).getTrack();
            }
            if (playable instanceof YouTubeVideo) {
                spotifyTrack = ((YouTubeVideo) playable).getRedirectedSpotifyTrack();
            }
            if (spotifyTrack != null) {
                title = spotifyTrack.getName();
                spotifyItemKind = spotifyTrack.exhaustiveMatch(
                    track -> {
                        for (ArtistSimplified artist : track.getArtists()) {
                            Artist artistEntity = Artist.getOrCreateArtist(artist, session);
                            artists.add(artistEntity);
                        }

                        return LookupEntity.require(session, SpotifyItemKind.class, SpotifyTrackKind.TRACK.name());
                    },
                    episode -> LookupEntity.require(session, SpotifyItemKind.class, SpotifyTrackKind.EPISODE.name())
                );
            } else {
                title = playable.getDisplay();
            }
            this.source = LookupEntity.require(session, PlaybackHistorySource.class, playable.getSource().name());
            this.trackId = playable.getId();
            this.guild = guild.getName();
            this.guildId = guild.getId();
        } catch (UnavailableResourceException e) {
            // should never happen since when a track is being played that obviously means it was loaded
            throw new RuntimeException("trying to create a history for a track that didn't load successfully");
        }
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
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

    public Set<Artist> getArtists() {
        return artists;
    }

    public void setArtists(Set<Artist> artists) {
        this.artists = artists;
    }

    public Set<UserPlaybackHistory> getUserPlaybackHistories() {
        return userPlaybackHistories;
    }

    public void setUserPlaybackHistories(Set<UserPlaybackHistory> userPlaybackHistories) {
        this.userPlaybackHistories = userPlaybackHistories;
    }

    public void addUserPlaybackHistory(UserPlaybackHistory userPlaybackHistory) {
        userPlaybackHistories.add(userPlaybackHistory);
    }

    public PlaybackHistorySource getSource() {
        return source;
    }

    public void setSource(PlaybackHistorySource source) {
        this.source = source;
    }

    public SpotifyItemKind getSpotifyItemKind() {
        return spotifyItemKind;
    }

    public void setSpotifyItemKind(SpotifyItemKind spotifyItemKind) {
        this.spotifyItemKind = spotifyItemKind;
    }
}
