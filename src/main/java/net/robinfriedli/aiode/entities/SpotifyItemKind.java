package net.robinfriedli.aiode.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;

// TODO create validators to check that videos with a redirected spotify track and playback history entries of type "spotify" have a kind
@Entity
@Table(name = "spotify_item_kind")
public class SpotifyItemKind extends LookupEntity {

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

    public SpotifyTrackKind asEnum() {
        return SpotifyTrackKind.valueOf(getUniqueId());
    }

}
