package net.robinfriedli.aiode.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.audio.UrlPlayable;

@Entity
@Table(name = "url_track")
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
