package net.robinfriedli.aiode.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.robinfriedli.aiode.audio.spotify.SpotifyRedirectService;
import net.robinfriedli.aiode.cron.tasks.RefreshSpotifyRedirectIndicesTask;

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
