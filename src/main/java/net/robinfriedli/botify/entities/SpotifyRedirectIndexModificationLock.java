package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import net.robinfriedli.botify.audio.spotify.SpotifyRedirectService;
import net.robinfriedli.botify.cron.tasks.RefreshSpotifyRedirectIndicesTask;

/**
 * Creates a lock while running the {@link RefreshSpotifyRedirectIndicesTask} to signal the {@link SpotifyRedirectService}
 * not to update or delete any {@link SpotifyRedirectIndex} entities
 */
@Entity
@Table(name = "spotify_redirect_index_modification_lock")
public class SpotifyRedirectIndexModificationLock implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "creation_time_stamp")
    private LocalDateTime creationTimeStamp;

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public LocalDateTime getCreationTimeStamp() {
        return creationTimeStamp;
    }

    public void setCreationTimeStamp(LocalDateTime creationTimeStamp) {
        this.creationTimeStamp = creationTimeStamp;
    }
}
