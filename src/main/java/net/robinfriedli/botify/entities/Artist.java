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
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import org.hibernate.Session;

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
        Botify botify = Botify.get();
        XSync<String> stringSync = botify.getStringSync();
        QueryBuilderFactory queryBuilderFactory = botify.getQueryBuilderFactory();
        return stringSync.evaluate(artist.getId(), () -> {
            Optional<Artist> existingArtist = queryBuilderFactory.find(Artist.class)
                .where((cb, root) -> cb.equal(root.get("id"), artist.getId()))
                .build(session)
                .uniqueResultOptional();
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
