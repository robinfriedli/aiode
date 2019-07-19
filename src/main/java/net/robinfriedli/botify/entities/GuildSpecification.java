package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.google.api.client.util.Sets;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;

@Entity
@Table(name = "guild_specification")
public class GuildSpecification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "guild_name")
    private String guildName;
    @Column(name = "guild_id")
    private String guildId;
    @Column(name = "bot_name")
    private String botName;
    @Column(name = "prefix")
    private String prefix;
    @Column(name = "send_playback_notification")
    private Boolean sendPlaybackNotification;
    @Column(name = "color")
    private String color;
    @OneToMany(mappedBy = "guildSpecification", fetch = FetchType.EAGER)
    private Set<AccessConfiguration> accessConfigurations = Sets.newHashSet();

    public GuildSpecification() {
    }

    public GuildSpecification(Guild guild) {
        this.guildId = guild.getId();
        guildName = guild.getName();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getGuildName() {
        return guildName;
    }

    public void setGuildName(String guildName) {
        this.guildName = guildName;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guild) {
        this.guildId = guild;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botName) {
        this.botName = botName;
    }

    @Nullable
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void addAccessConfiguration(AccessConfiguration accessConfiguration) {
        accessConfiguration.setGuildSpecification(this);
        accessConfigurations.add(accessConfiguration);
    }

    public boolean removeAccessConfiguration(AccessConfiguration accessConfiguration) {
        return accessConfigurations.remove(accessConfiguration);
    }

    public Set<AccessConfiguration> getAccessConfigurations() {
        return accessConfigurations;
    }

    public Optional<AccessConfiguration> getAccessConfiguration(String commandIdentifier) {
        return accessConfigurations
            .stream()
            .filter(accessConfiguration -> accessConfiguration.getCommandIdentifier().equals(commandIdentifier))
            .findAny();
    }

    public Guild getGuild(JDA jda) {
        return jda.getGuildById(getGuildId());
    }

    public Boolean isSendPlaybackNotification() {
        return sendPlaybackNotification;
    }

    public void setSendPlaybackNotification(Boolean sendPlaybackNotification) {
        this.sendPlaybackNotification = sendPlaybackNotification;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
