package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import org.jetbrains.annotations.Nullable;

@Entity
@Table(name = "custom_permission_target")
public class CustomPermissionTarget implements PermissionTarget, SanitizedEntity, Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "identifier", nullable = false)
    @Size(min = 1, max = 30, message = "Invalid length of permission name. Needs to be between 1 and 30.")
    private String identifier;
    @Column(name = "guild_id", nullable = false)
    private long guildId;
    @Column(name = "guild")
    private String guild;
    @Column(name = "user_id")
    private long userId;
    @Column(name = "user_name")
    private String user;

    public CustomPermissionTarget() {
    }

    public CustomPermissionTarget(String identifier, Guild guild, User user) {
        this.identifier = identifier;
        this.guildId = guild.getIdLong();
        this.guild = guild.getName();
        this.userId = user.getIdLong();
        this.user = user.getName();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    @Override
    public int getMaxEntityCount(SpringPropertiesConfig springPropertiesConfig) {
        Integer applicationProperty = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.custom_permission_target_max");
        return Objects.requireNonNullElse(applicationProperty, 0);
    }

    @Override
    public String getIdentifierPropertyName() {
        return "identifier";
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public void setSanitizedIdentifier(String sanitizedIdentifier) {
        setIdentifier(sanitizedIdentifier);
    }

    @Override
    public CountUnit createCountUnit(int maxEntityCount, QueryBuilderFactory queryBuilderFactory) {
        return new CountUnit(
            getClass(),
            this,
            session -> queryBuilderFactory.select(getClass(), ((from, cb) -> cb.count(from.get("pk"))), Long.class),
            String.format("Maximum custom permission target count of %s reached", maxEntityCount),
            maxEntityCount
        );
    }

    @Override
    public Set<IdentifierFormattingRule> getIdentifierFormattingRules() {
        return Collections.singleton(
            new IdentifierFormattingRule(
                "Permission identifier '%s' is invalid. Permission identifiers cannot contain the character ',' " +
                    "as it is reserved as an enumeration separator or the character '$' as it is reserved for child permissions such as arguments.",
                s -> s.indexOf('$') < 0 && s.indexOf(',') < 0
            )
        );
    }

    public long getGuildId() {
        return guildId;
    }

    public void setGuildId(long guildId) {
        this.guildId = guildId;
    }

    public String getGuild() {
        return guild;
    }

    public void setGuild(String guild) {
        this.guild = guild;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @Override
    public String getPermissionTargetIdentifier() {
        return getIdentifier();
    }

    @Override
    public TargetType getPermissionTargetType() {
        return TargetType.CUSTOM;
    }

    @Nullable
    @Override
    public PermissionTarget getChildTarget(String identifier) {
        return null;
    }

    @Nullable
    @Override
    public Set<? extends PermissionTarget> getChildren() {
        return null;
    }

    @Nullable
    @Override
    public PermissionTarget getParentTarget() {
        return null;
    }
}
