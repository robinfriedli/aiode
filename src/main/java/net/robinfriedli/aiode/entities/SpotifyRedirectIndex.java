package net.robinfriedli.aiode.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Optional;

import com.google.common.base.Strings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;
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
    @Column(name = "filebroker_pk")
    private Long fileBrokerPk;
    @Column(name = "soundcloud_uri")
    private String soundCloudUri;
    @Column(name = "last_updated")
    private LocalDate lastUpdated;
    @Column(name = "last_used")
    private LocalDate lastUsed;
    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_spotify_item_kind", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "spotify_redirect_index_fk_spotify_item_kind_fkey"))
    private SpotifyItemKind spotifyItemKind;

    public SpotifyRedirectIndex() {
    }

    public SpotifyRedirectIndex(String spotifyId, String youTubeId, Long fileBrokerPk, String soundCloudUri, SpotifyTrackKind kind, Session session) {
        this.spotifyId = spotifyId;
        this.youTubeId = youTubeId;
        this.fileBrokerPk = fileBrokerPk;
        this.soundCloudUri = soundCloudUri;
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

    public Long getFileBrokerPk() {
        return fileBrokerPk;
    }

    public void setFileBrokerPk(Long fileBrokerPk) {
        this.fileBrokerPk = fileBrokerPk;
    }

    public String getSoundCloudUri() {
        return soundCloudUri;
    }

    public void setSoundCloudUri(String soundCloudUri) {
        this.soundCloudUri = soundCloudUri;
    }
}
