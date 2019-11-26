package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.util.Optional;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.antkorwin.xsync.XSync;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import net.robinfriedli.botify.Botify;
import org.hibernate.Session;
import org.hibernate.query.Query;

@Entity
@Table(name = "artist")
public class Artist implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "id", unique = true)
    private String id;
    @Column(name = "name")
    private String name;

    public Artist() {
    }

    public Artist(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Get the existing artist with the provided or create a new artist. This method runs synchronised on the artist id
     * to avoid unique constraint violations during concurrent creation.
     *
     * @param artist  the spotify artist
     * @param session the hibernate session
     */
    public static Artist getOrCreateArtist(ArtistSimplified artist, Session session) {
        XSync<String> stringSync = Botify.get().getStringSync();
        return stringSync.evaluate(artist.getId(), () -> {
            Query<Artist> query = session
                .createQuery(" from " + Artist.class.getName() + " where id = '" + artist.getId() + "'", Artist.class);
            Optional<Artist> existingArtist = query.uniqueResultOptional();
            return existingArtist.orElseGet(() -> {
                Artist newArtist = new Artist(artist.getId(), artist.getName());
                session.persist(newArtist);
                return newArtist;
            });
        });
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
}
