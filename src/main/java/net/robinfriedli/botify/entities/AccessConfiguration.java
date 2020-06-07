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
    @Column(name = "command_identifier", nullable = false)
    private String commandIdentifier;
    @OneToMany(mappedBy = "accessConfiguration", fetch = FetchType.EAGER)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<GrantedRole> roles = Sets.newHashSet();
    @ManyToOne
    @JoinColumn(name = "guild_specification_pk", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "fk_guild_specification"))
    private GuildSpecification guildSpecification;

    public AccessConfiguration() {
    }

    public AccessConfiguration(String commandIdentifier) {
        this.commandIdentifier = commandIdentifier;
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

    public String getCommandIdentifier() {
        return commandIdentifier;
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
}
