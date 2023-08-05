package net.robinfriedli.aiode.entities;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "global_track_chart")
public class GlobalTrackChart implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Column(name = "track_id", nullable = false)
    private String trackId;

    @Column(name = "count", nullable = false)
    private long count;

    @ManyToOne
    @JoinColumn(name = "fk_source", referencedColumnName = "pk", nullable = false, foreignKey = @ForeignKey(name = "global_track_chart_fk_source_fkey"))
    private PlaybackHistorySource source;

    @ManyToOne
    @JoinColumn(name = "fk_spotify_item_kind", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "global_track_chart_fk_spotify_item_kind_fkey"))
    private SpotifyItemKind spotifyItemKind;

    @Column(name = "is_monthly")
    private boolean isMonthly = false;

    @Column(name = "fk_source", nullable = false, insertable = false, updatable = false)
    private long fkSource;
    @Column(name = "fk_spotify_item_kind", insertable = false, updatable = false)
    private long fkSpotifyItemKind;

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
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

    public boolean isMonthly() {
        return isMonthly;
    }

    public void setMonthly(boolean monthly) {
        isMonthly = monthly;
    }

    public long getFkSource() {
        return fkSource;
    }

    public void setFkSource(long fkSource) {
        this.fkSource = fkSource;
    }

    public long getFkSpotifyItemKind() {
        return fkSpotifyItemKind;
    }

    public void setFkSpotifyItemKind(long fkSpotifyItemKind) {
        this.fkSpotifyItemKind = fkSpotifyItemKind;
    }
}
