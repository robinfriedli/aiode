package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Optional;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.google.common.base.Strings;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackKind;
import org.hibernate.FlushMode;
import org.hibernate.Session;

/**
 * Table that maps the resulting YouTube video to the original Spotify track when redirecting a Spotify track so that when
 * playing this track again the corresponding YouTube video can be loaded from the database which speeds up the
 * redirection process and saves YouTube API quota
 */
@Entity
@Table(name = "spotify_redirect_index")
public class SpotifyRedirectIndex implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "spotify_id", unique = true)
    private String spotifyId;
    @Column(name = "youtube_id")
    private String youTubeId;
    @Column(name = "last_updated")
    private LocalDate lastUpdated;
    @Column(name = "last_used")
    private LocalDate lastUsed;
    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_spotify_item_kind", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "spotify_redirect_index_fk_spotify_item_kind_fkey"))
    private SpotifyItemKind spotifyItemKind;

    public SpotifyRedirectIndex() {
    }

    public SpotifyRedirectIndex(String spotifyId, String youTubeId, SpotifyTrackKind kind, Session session) {
        this.spotifyId = spotifyId;
        this.youTubeId = youTubeId;
        lastUpdated = LocalDate.now();
        lastUsed = LocalDate.now();
        spotifyItemKind = LookupEntity.require(session, SpotifyItemKind.class, kind.name());
    }

    public static Optional<SpotifyRedirectIndex> queryExistingIndex(Session session, String spotifyTrackId) {
        if (Strings.isNullOrEmpty(spotifyTrackId)) {
            return Optional.empty();
        }
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<SpotifyRedirectIndex> query = cb.createQuery(SpotifyRedirectIndex.class);
        Root<SpotifyRedirectIndex> root = query.from(SpotifyRedirectIndex.class);
        query.where(cb.equal(root.get("spotifyId"), spotifyTrackId));
        return session.createQuery(query).setHibernateFlushMode(FlushMode.MANUAL).uniqueResultOptional();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getSpotifyId() {
        return spotifyId;
    }

    public void setSpotifyId(String spotifyId) {
        this.spotifyId = spotifyId;
    }

    public String getYouTubeId() {
        return youTubeId;
    }

    public void setYouTubeId(String youTubeId) {
        this.youTubeId = youTubeId;
    }

    public LocalDate getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDate lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDate getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(LocalDate lastUsed) {
        this.lastUsed = lastUsed;
    }

    public SpotifyItemKind getSpotifyItemKind() {
        return spotifyItemKind;
    }

    public void setSpotifyItemKind(SpotifyItemKind spotifyItemKind) {
        this.spotifyItemKind = spotifyItemKind;
    }
}
