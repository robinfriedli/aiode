package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.FlushModeType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import com.google.common.collect.Lists;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableImpl;
import net.robinfriedli.botify.audio.YouTubeVideo;
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
    private Date timestamp;
    @Column(name = "title")
    private String title;
    @Column(name = "source")
    private String source;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @ManyToMany(fetch = FetchType.EAGER)
    private List<Artist> artists = Lists.newArrayList();

    public PlaybackHistory() {
    }

    public PlaybackHistory(Date timestamp, Playable playable, Guild guild, Session session) {
        this.timestamp = timestamp;
        Track track = null;
        if (playable instanceof PlayableImpl) {
            Object delegate = ((PlayableImpl) playable).delegate();
            if (delegate instanceof Track) {
                track = (Track) delegate;
            } else if (delegate instanceof YouTubeVideo) {
                track = ((YouTubeVideo) delegate).getRedirectedSpotifyTrack();
            }
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
            title = playable.getDisplayInterruptible();
        }
        this.source = playable.getSource();
        this.guild = guild.getName();
        this.guildId = guild.getId();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
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

    public List<Artist> getArtists() {
        return artists;
    }

    public void setArtists(List<Artist> artists) {
        this.artists = artists;
    }
}
