package net.robinfriedli.botify.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.audio.youtube.YouTubeVideo;
import net.robinfriedli.botify.audio.youtube.YouTubeVideoImpl;
import net.robinfriedli.botify.exceptions.UnavailableResourceException;

@Entity
@Table(name = "video")
public class Video extends PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "id")
    private String id;
    @Column(name = "title")
    private String title;
    @Column(name = "redirected_spotify_id")
    private String redirectedSpotifyId;
    @Column(name = "spotify_track_name")
    private String spotifyTrackName;

    public Video() {
    }

    public Video(YouTubeVideo video, User user, Playlist playlist) {
        super(user, playlist);
        try {
            id = video.getVideoId();
            title = video.getDisplay();
            duration = video.getDuration();

            Track redirectedSpotifyTrack = video.getRedirectedSpotifyTrack();
            if (redirectedSpotifyTrack != null) {
                redirectedSpotifyId = redirectedSpotifyTrack.getId();
                spotifyTrackName = redirectedSpotifyTrack.getName();
            }
        } catch (UnavailableResourceException e) {
            throw new RuntimeException("Cannot create video element for cancelled YouTube video " + video.toString(), e);
        }
    }

    @Override
    public PlaylistItem copy(Playlist playlist) {
        Video video = new Video();
        video.setId(getId());
        video.setTitle(getTitle());
        video.setRedirectedSpotifyId(getRedirectedSpotifyId());
        video.setSpotifyTrackName(getSpotifyTrackName());
        video.setDuration(getDuration());
        video.setAddedUser(getAddedUser());
        video.setAddedUserId(getAddedUserId());
        video.setPlaylist(playlist);
        return video;
    }

    @Override
    public boolean matches(String searchTerm) {
        return title.equalsIgnoreCase(searchTerm) || (spotifyTrackName != null && spotifyTrackName.equalsIgnoreCase(searchTerm));
    }

    @Override
    public String display() {
        return title;
    }

    @Override
    public void add() {
        getPlaylist().getVideos().add(this);
    }

    public YouTubeVideo asYouTubeVideo() {
        return new YouTubeVideoImpl(getTitle(), getId(), getDuration());
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRedirectedSpotifyId() {
        return redirectedSpotifyId;
    }

    public void setRedirectedSpotifyId(String redirectedSpotifyId) {
        this.redirectedSpotifyId = redirectedSpotifyId;
    }

    public String getSpotifyTrackName() {
        return spotifyTrackName;
    }

    public void setSpotifyTrackName(String spotifyTrackName) {
        this.spotifyTrackName = spotifyTrackName;
    }

}
