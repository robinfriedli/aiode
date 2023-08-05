package net.robinfriedli.aiode.entities;


import java.io.Serializable;
import java.util.Optional;

import javax.annotation.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import net.robinfriedli.aiode.Aiode;
import org.hibernate.Session;

@MappedSuperclass
public class LookupEntity implements Serializable {

    @Column(name = "unique_id", nullable = false, unique = true)
    private String uniqueId;

    public static <T extends LookupEntity> T require(Session session, Class<T> type, String id) {
        return getOptional(session, type, id).orElseThrow();
    }

    @Nullable
    public static <T extends LookupEntity> T get(Session session, Class<T> type, String id) {
        return getOptional(session, type, id).orElse(null);
    }

    public static <T extends LookupEntity> Optional<T> getOptional(Session session, Class<T> type, String id) {
        return Aiode.get().getQueryBuilderFactory().find(type)
            .where(((cb, root) -> cb.equal(root.get("uniqueId"), id)))
            .build(session)
            .uniqueResultOptional();
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

}
