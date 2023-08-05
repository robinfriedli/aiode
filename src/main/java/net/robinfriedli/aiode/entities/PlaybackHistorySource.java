package net.robinfriedli.aiode.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.robinfriedli.aiode.audio.Playable;

@Entity
@Table(name = "playback_history_source")
public class PlaybackHistorySource extends LookupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public Playable.Source asEnum() {
        return Playable.Source.valueOf(getUniqueId());
    }

}
