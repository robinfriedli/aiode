package net.robinfriedli.botify.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;

@Entity
@Table(name = "granted_role")
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
