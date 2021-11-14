package net.robinfriedli.botify.entities;

import java.io.IOException;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.hc.core5.http.ParseException;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.entities.User;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.ShowSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

/**
 * Entity representing Spotify Podcast episodes
 */
@Entity
@Table(name = "episode")
public class Episode extends PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "id")
    private String id;
    @Column(name = "name")
    private String name;
    @Column(name = "show_id")
    private String showId;
    @Column(name = "show_name")
    private String showName;

    public Episode() {
    }

    public Episode(se.michaelthelin.spotify.model_objects.specification.Episode episode, User user, Playlist playlist) {
        super(user, playlist);
        id = episode.getId();
        name = episode.getName();
        duration = episode.getDurationMs();
        initShow(episode);
    }

    public Episode(se.michaelthelin.spotify.model_objects.specification.Episode episode, String addedUser, String addedUserId, Playlist playlist) {
        super(addedUser, addedUserId, playlist);
        id = episode.getId();
        name = episode.getName();
        duration = episode.getDurationMs();
        initShow(episode);
    }

    private void initShow(se.michaelthelin.spotify.model_objects.specification.Episode episode) {
        ShowSimplified show = episode.getShow();
        if (show != null) {
            showId = show.getId();
            showName = show.getName();
        }
    }

    @Override
    public PlaylistItem copy(Playlist playlist) {
        Episode episode = new Episode();
        episode.setId(getId());
        episode.setName(getName());
        episode.setShowId(getShowId());
        episode.setShowName(getShowName());
        episode.setDuration(getDuration());
        episode.setAddedUser(getAddedUser());
        episode.setAddedUserId(getAddedUserId());
        episode.setPlaylist(playlist);
        return episode;
    }

    @Override
    public boolean matches(String searchTerm) {
        return name.equalsIgnoreCase(searchTerm);
    }

    @Override
    public String display() {
        if (Strings.isNullOrEmpty(showName)) {
            return name;
        }

        return name + " by " + showName;
    }

    @Override
    public void add() {
        getPlaylist().getEpisodes().add(this);
    }

    public Track asTrack(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException, ParseException {
        return spotifyApi.getTrack(getId()).build().execute();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShowId() {
        return showId;
    }

    public void setShowId(String showId) {
        this.showId = showId;
    }

    public String getShowName() {
        return showName;
    }

    public void setShowName(String showName) {
        this.showName = showName;
    }
}
