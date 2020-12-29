package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.google.common.collect.Sets;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.robinfriedli.botify.command.PermissionTarget;
import org.hibernate.Session;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "access_configuration")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class AccessConfiguration implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "permission_identifier", nullable = false)
    private String permissionIdentifier;
    @OneToMany(mappedBy = "accessConfiguration", fetch = FetchType.EAGER)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<GrantedRole> roles = Sets.newHashSet();
    @ManyToOne
    @JoinColumn(name = "fk_guild_specification", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "access_configuration_fk_guild_specification_fkey"))
    private GuildSpecification guildSpecification;
    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_permission_type", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "access_configuration_fk_permission_type_fkey"))
    private PermissionType permissionType;

    public AccessConfiguration() {
    }

    public AccessConfiguration(String commandIdentifier, Session session) {
        this.permissionIdentifier = commandIdentifier;
        this.permissionType = PermissionTarget.TargetType.COMMAND.getEntity(session);
    }

    public AccessConfiguration(PermissionTarget permissionTarget, Session session) {
        this.permissionIdentifier = permissionTarget.getFullPermissionTargetIdentifier();
        this.permissionType = permissionTarget.getPermissionTargetType().getEntity(session);
    }

    public boolean canAccess(Member member) {
        Set<String> roles = getRoles().stream().map(GrantedRole::getId).collect(Collectors.toSet());

        if (roles.isEmpty()) {
            return false;
        }

        Set<String> memberRoles = member.getRoles().stream().map(ISnowflake::getId).collect(Collectors.toSet());
        return memberRoles.stream().anyMatch(roles::contains);
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getPermissionIdentifier() {
        return permissionIdentifier;
    }

    public Optional<GrantedRole> getRole(String id) {
        return getRoles().stream().filter(role -> role.getId().equals(id)).findAny();
    }

    public void addRole(GrantedRole role) {
        role.setAccessConfiguration(this);
        roles.add(role);
    }

    public void removeRole(GrantedRole role) {
        roles.remove(role);
    }

    public boolean hasRole(String id) {
        return getRoles().stream().anyMatch(role -> role.getId().equals(id));
    }

    public Set<String> getRoleIds() {
        return getRoles().stream().map(GrantedRole::getId).collect(Collectors.toSet());
    }

    public Set<GrantedRole> getRoles() {
        return Sets.newHashSet(roles);
    }

    public void setRoles(Set<GrantedRole> roles) {
        this.roles = roles;
    }

    public List<Role> getRoles(Guild guild) {
        return getRoles().stream().map(role -> role.getRole(guild)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public GuildSpecification getGuildSpecification() {
        return guildSpecification;
    }

    public void setGuildSpecification(GuildSpecification guildSpecification) {
        this.guildSpecification = guildSpecification;
    }

    public PermissionType getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(PermissionType permissionType) {
        this.permissionType = permissionType;
    }

    @Entity
    @Table(name = "permission_type")
    public static class PermissionType extends LookupEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "pk")
        private long pk;

        public long getPk() {
            return pk;
        }

        public void setPk(long pk) {
            this.pk = pk;
        }

        public PermissionTarget.TargetType asEnum() {
            return PermissionTarget.TargetType.valueOf(getUniqueId());
        }

    }

}
