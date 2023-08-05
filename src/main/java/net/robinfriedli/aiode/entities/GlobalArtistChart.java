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
@Table(name = "global_artist_chart")
public class GlobalArtistChart implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Column(name = "count", nullable = false)
    private long count;

    @ManyToOne
    @JoinColumn(name = "artist_pk", referencedColumnName = "pk", nullable = false, foreignKey = @ForeignKey(name = "global_artist_chart_artist_pk_fkey"))
    private Artist artist;

    @Column(name = "is_monthly")
    private boolean isMonthly = false;

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public Artist getArtist() {
        return artist;
    }

    public void setArtist(Artist source) {
        this.artist = source;
    }

    public boolean isMonthly() {
        return isMonthly;
    }

    public void setMonthly(boolean monthly) {
        isMonthly = monthly;
    }
}
