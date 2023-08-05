package net.robinfriedli.aiode.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.audio.UrlPlayable;

@Entity
@Table(name = "url_track", indexes = {
    @Index(name = "url_track_url_idx", columnList = "url"),
    @Index(name = "url_track_playlist_pk_idx", columnList = "playlist_pk")
})
public class UrlTrack extends PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "url")
    private String url;
    @Column(name = "title")
    private String title;

    public UrlTrack() {
    }

    public UrlTrack(UrlPlayable playable, User user, Playlist playlist) {
        super(user, playlist);
        this.url = playable.getPlaybackUrl();
        this.title = playable.getDisplay();
        this.duration = playable.getDurationMs();
    }

    @Override
    public PlaylistItem copy(Playlist playlist) {
        UrlTrack urlTrack = new UrlTrack();
        urlTrack.setUrl(getUrl());
        urlTrack.setTitle(getTitle());
        urlTrack.setDuration(getDuration());
        urlTrack.setAddedUser(getAddedUser());
        urlTrack.setAddedUserId(getAddedUserId());
        urlTrack.setPlaylist(playlist);
        return urlTrack;
    }

    @Override
    public boolean matches(String searchTerm) {
        return title.equalsIgnoreCase(searchTerm) || url.equals(searchTerm);
    }

    @Override
    public String display() {
        return title;
    }

    @Override
    public void add() {
        getPlaylist().getUrlTracks().add(this);
    }

    public UrlPlayable asPlayable() {
        return new UrlPlayable(this);
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}
