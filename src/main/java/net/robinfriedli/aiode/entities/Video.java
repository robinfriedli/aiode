package net.robinfriedli.aiode.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideo;
import net.robinfriedli.aiode.audio.youtube.YouTubeVideoImpl;
import net.robinfriedli.aiode.exceptions.UnavailableResourceException;
import org.hibernate.Session;

@Entity
@Table(name = "video", indexes = {
    @Index(name = "video_id_idx", columnList = "id"),
    @Index(name = "video_playlist_pk_idx", columnList = "playlist_pk")
})
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
    @ManyToOne
    @JoinColumn(name = "fk_redirected_spotify_kind", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "video_fk_redirected_spotify_kind_fkey"))
    private SpotifyItemKind redirectedSpotifyKind;

    public Video() {
    }

    public Video(YouTubeVideo video, User user, Playlist playlist, Session session) {
        super(user, playlist);
        try {
            id = video.getVideoId();
            title = video.getDisplay();
            duration = video.getDuration();

            SpotifyTrack redirectedSpotifyTrack = video.getRedirectedSpotifyTrack();
            if (redirectedSpotifyTrack != null) {
                redirectedSpotifyId = redirectedSpotifyTrack.getId();
                spotifyTrackName = redirectedSpotifyTrack.getName();
                redirectedSpotifyKind = redirectedSpotifyTrack.exhaustiveMatch(
                    track -> LookupEntity.require(session, SpotifyItemKind.class, SpotifyTrackKind.TRACK.name()),
                    episode -> LookupEntity.require(session, SpotifyItemKind.class, SpotifyTrackKind.EPISODE.name())
                );
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

    public SpotifyItemKind getRedirectedSpotifyKind() {
        return redirectedSpotifyKind;
    }

    public void setRedirectedSpotifyKind(SpotifyItemKind redirectedSpotifyKind) {
        this.redirectedSpotifyKind = redirectedSpotifyKind;
    }
}
