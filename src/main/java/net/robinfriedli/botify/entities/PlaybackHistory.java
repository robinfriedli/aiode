package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FlushModeType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.google.api.client.util.Sets;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.spotify.TrackWrapper;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;
import org.hibernate.Session;
import org.hibernate.query.Query;

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
    @Column(name = "source")
    private String source;
    @Column(name = "track_id")
    private String trackId;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @ManyToMany
    private Set<Artist> artists = Sets.newHashSet();
    @OneToMany(mappedBy = "playbackHistory")
    private Set<UserPlaybackHistory> userPlaybackHistories = Sets.newHashSet();

    public PlaybackHistory() {
    }

    public PlaybackHistory(LocalDateTime timestamp, Playable playable, Guild guild, Session session) {
        try {
            this.timestamp = timestamp;
            Track track = null;
            if (playable instanceof TrackWrapper) {
                track = ((TrackWrapper) playable).getTrack();
            }
            if (playable instanceof YouTubeVideo) {
                track = ((YouTubeVideo) playable).getRedirectedSpotifyTrack();
            }
            if (track != null) {
                title = track.getName();
                for (ArtistSimplified artist : track.getArtists()) {
                    Query<Artist> query = session
                        .createQuery(" from " + Artist.class.getName() + " where id = '" + artist.getId() + "'", Artist.class);
                    query.setFlushMode(FlushModeType.AUTO);
                    Optional<Artist> optionalArtist = query.uniqueResultOptional();
                    if (optionalArtist.isPresent()) {
                        artists.add(optionalArtist.get());
                    } else {
                        Artist newArtist = new Artist(artist.getId(), artist.getName());
                        session.persist(newArtist);
                        artists.add(newArtist);
                    }
                }
            } else {
                title = playable.getDisplay();
            }
            this.source = playable.getSource();
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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

}
