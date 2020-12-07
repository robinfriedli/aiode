package net.robinfriedli.botify.entities;

import java.io.IOException;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import org.apache.hc.core5.http.ParseException;

import com.google.common.collect.Sets;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.stringlist.StringList;
import org.hibernate.Session;

@Entity
@Table(name = "song")
public class Song extends PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "id")
    private String id;
    @Column(name = "name")
    private String name;
    @ManyToMany
    private Set<Artist> artists = Sets.newHashSet();

    public Song() {
    }

    public Song(Track track, User user, Playlist playlist, Session session) {
        super(user, playlist);
        id = track.getId();
        name = track.getName();
        duration = track.getDurationMs();
        initArtists(track, session);
    }

    public Song(Track track, String addedUser, String addedUserId, Playlist playlist, Session session) {
        super(addedUser, addedUserId, playlist);
        id = track.getId();
        name = track.getName();
        duration = track.getDurationMs();
        initArtists(track, session);
    }

    @Override
    public PlaylistItem copy(Playlist playlist) {
        Song song = new Song();
        song.setId(getId());
        song.setName(getName());
        song.setArtists(Sets.newHashSet(getArtists()));
        song.setDuration(getDuration());
        song.setAddedUser(getAddedUser());
        song.setAddedUserId(getAddedUserId());
        song.setPlaylist(playlist);
        return song;
    }

    @Override
    public boolean matches(String searchTerm) {
        return name.equalsIgnoreCase(searchTerm);
    }

    @Override
    public String display() {
        String artistString = StringList.create(artists, Artist::getName).toSeparatedString(", ");
        return name + " by " + artistString;
    }

    @Override
    public void add() {
        getPlaylist().getSongs().add(this);
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

    public Set<Artist> getArtists() {
        return artists;
    }

    public void setArtists(Set<Artist> artists) {
        this.artists = artists;
    }

    private void initArtists(Track track, Session session) {
        for (ArtistSimplified artist : track.getArtists()) {
            if (artist.getId() != null) {
                artists.add(Artist.getOrCreateArtist(artist, session));
            }
        }
    }

}
