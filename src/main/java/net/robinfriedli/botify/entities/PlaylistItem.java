package net.robinfriedli.botify.entities;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

import net.dv8tion.jda.core.entities.User;

@MappedSuperclass
public abstract class PlaylistItem {

    @Column(name = "duration")
    long duration;
    @Column(name = "added_user")
    private String addedUser;
    @Column(name = "added_user_id")
    private String addedUserId;
    @ManyToOne
    private Playlist playlist;
    @Column(name = "created_timestamp")
    private Date createdTimestamp;

    public PlaylistItem() {
    }

    public PlaylistItem(User user, Playlist playlist) {
        this.addedUser = user.getName();
        this.addedUserId = user.getId();
        this.playlist = playlist;
    }

    public abstract PlaylistItem copy(Playlist playlist);

    public abstract boolean matches(String searchTerm);

    public abstract String display();

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getAddedUser() {
        return addedUser;
    }

    public void setAddedUser(String addedUser) {
        this.addedUser = addedUser;
    }

    public String getAddedUserId() {
        return addedUserId;
    }

    public void setAddedUserId(String addedUserId) {
        this.addedUserId = addedUserId;
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
    }

    public Date getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(Date createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}
