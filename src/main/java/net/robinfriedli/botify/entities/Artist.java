package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.util.Optional;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.exec.Mode;
import net.robinfriedli.exec.MutexSync;
import net.robinfriedli.exec.modes.MutexSyncMode;
import org.hibernate.Session;

@Entity
@Table(name = "artist")
public class Artist implements Serializable {

    private static final MutexSync<String> ARTIST_SYNC = new MutexSync<>();

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
        QueryBuilderFactory queryBuilderFactory = Botify.get().getQueryBuilderFactory();
        Mode mode = Mode.create().with(new MutexSyncMode<>(artist.getId(), ARTIST_SYNC));
        return HibernateInvoker.create(session).invokeFunction(mode, currentSession -> {
            Optional<Artist> existingArtist = queryBuilderFactory.find(Artist.class)
                .where((cb, root) -> cb.equal(root.get("id"), artist.getId()))
                .build(currentSession)
                .uniqueResultOptional();
            return existingArtist.orElseGet(() -> {
                Artist newArtist = new Artist(artist.getId(), artist.getName());
                currentSession.persist(newArtist);
                currentSession.flush();
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
