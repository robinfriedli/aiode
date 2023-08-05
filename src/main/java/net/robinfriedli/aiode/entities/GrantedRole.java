package net.robinfriedli.aiode.entities;

import java.io.Serializable;

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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

@Entity
@Table(name = "granted_role", indexes = {
    @Index(name = "granted_role_id_idx", columnList = "id")
})
public class GrantedRole implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column
    private String id;
    @ManyToOne
    @JoinColumn(name = "access_configuration_pk", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "fk_access_configuration"))
    private AccessConfiguration accessConfiguration;

    public GrantedRole() {
    }

    public GrantedRole(Role role) {
        this.id = role.getId();
    }

    public Role getRole(Guild guild) {
        return guild.getRoleById(id);
    }

    public String getId() {
        return id;
    }

    public long getPk() {
        return pk;
    }

    public AccessConfiguration getAccessConfiguration() {
        return accessConfiguration;
    }

    public void setAccessConfiguration(AccessConfiguration accessConfiguration) {
        this.accessConfiguration = accessConfiguration;
    }
}
