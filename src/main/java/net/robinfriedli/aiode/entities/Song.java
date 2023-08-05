package net.robinfriedli.aiode.entities;

import java.io.IOException;
import java.util.Set;

import org.apache.hc.core5.http.ParseException;

import com.google.common.collect.Sets;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.stringlist.StringList;
import org.hibernate.Session;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

@Entity
@Table(name = "song", indexes = {
    @Index(name = "song_id_idx", columnList = "id"),
    @Index(name = "song_playlist_pk_idx", columnList = "playlist_pk")
})
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
    @JoinTable(name = "song_artist", joinColumns = {@JoinColumn(name = "song_pk")}, inverseJoinColumns = {@JoinColumn(name = "artist_pk")})
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

    private void initArtists(Track track, Session session) {
        for (ArtistSimplified artist : track.getArtists()) {
            if (artist.getId() != null) {
                artists.add(Artist.getOrCreateArtist(artist, session));
            }
        }
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

}
