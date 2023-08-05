package net.robinfriedli.aiode.entities;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import net.dv8tion.jda.api.entities.User;

@MappedSuperclass
public abstract class PlaylistItem implements Serializable {

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
    @Column(name = "item_index")
    private Integer index;

    @SuppressWarnings("unused")
    @Column(name = "playlist_pk", insertable = false, updatable = false)
    private long playlistPk;

    private transient int ordinal;

    public PlaylistItem() {
    }

    public PlaylistItem(User user, Playlist playlist) {
        this.addedUser = user != null ? user.getName() : "UNKNOWN USER";
        this.addedUserId = user != null ? user.getId() : "system";
        this.playlist = playlist;
    }

    public PlaylistItem(String addedUser, String addedUserId, Playlist playlist) {
        this.addedUser = addedUser;
        this.addedUserId = addedUserId;
        this.playlist = playlist;
    }

    public abstract long getPk();

    public abstract PlaylistItem copy(Playlist playlist);

    public abstract boolean matches(String searchTerm);

    public abstract String display();

    public abstract void add();

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

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }
}
