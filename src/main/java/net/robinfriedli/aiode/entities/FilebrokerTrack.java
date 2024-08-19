package net.robinfriedli.aiode.entities;

import com.google.common.base.Strings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper;
import net.robinfriedli.filebroker.FilebrokerApi;

@Entity
@Table(name = "filebroker_track", indexes = {
    @Index(name = "filebroker_track_post_pk", columnList = "post_pk"),
    @Index(name = "filebroker_track_playlist_pk_idx", columnList = "playlist_pk")
})
public class FilebrokerTrack extends PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "post_pk", nullable = false)
    private long postPk;
    @Column(name = "title")
    private String title;
    @Column(name = "artist")
    private String artist;

    public FilebrokerTrack() {
    }

    public FilebrokerTrack(FilebrokerApi.Post post, User user, Playlist playlist) {
        super(user, playlist);
        this.postPk = post.getPk();
        this.title = post.getTitle();
        this.artist = post.getS3_object_metadata().getArtist();
        this.duration = new FilebrokerPlayableWrapper(post).getDurationNow();
    }

    @Override
    public long getPk() {
        return pk;
    }

    @Override
    public PlaylistItem copy(Playlist playlist) {
        FilebrokerTrack copy = new FilebrokerTrack();
        copy.setPostPk(getPostPk());
        copy.setTitle(getTitle());
        copy.setArtist(getArtist());
        copy.setDuration(getDuration());
        copy.setAddedUser(getAddedUser());
        copy.setAddedUserId(getAddedUserId());
        copy.setPlaylist(playlist);
        return copy;
    }

    @Override
    public boolean matches(String searchTerm) {
        return title.equalsIgnoreCase(searchTerm) || String.valueOf(postPk).equals(searchTerm);
    }

    @Override
    public String display() {
        if (Strings.isNullOrEmpty(artist)) {
            return String.format("%s by %s", title, artist);
        }
        return title;
    }

    @Override
    public void add() {
        getPlaylist().getFilebrokerTracks().add(this);
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public long getPostPk() {
        return postPk;
    }

    public void setPostPk(long postPk) {
        this.postPk = postPk;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }
}
