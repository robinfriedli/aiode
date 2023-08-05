package net.robinfriedli.aiode.entities;

import java.io.Serializable;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.api.client.util.Sets;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "guild_specification")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class GuildSpecification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "guild_name")
    private String guildName;
    @Column(name = "guild_id", unique = true)
    private String guildId;
    @Column(name = "bot_name")
    @Size(min = 1, max = 32, message = "Invalid length of nickname. Needs to be between 1 and 32.")
    private String botName;
    @Column(name = "prefix")
    @Size(min = 1, max = 5, message = "Invalid length of prefix. Needs to be between 1 and 5.")
    private String prefix;
    @Column(name = "send_playback_notification")
    private Boolean sendPlaybackNotification;
    @Column(name = "color")
    private String color;
    @Column(name = "enable_auto_pause")
    private Boolean enableAutoPause;
    @Column(name = "default_source")
    private String defaultSource;
    @Column(name = "default_list_source")
    private String defaultListSource;
    @Column(name = "argument_prefix")
    private Character argumentPrefix;
    @Column(name = "temp_message_timeout")
    private Integer tempMessageTimeout;
    @Column(name = "default_text_channel_id")
    private String defaultTextChannelId;
    @Column(name = "default_volume")
    private Integer defaultVolume;
    @Column(name = "auto_queue_mode")
    private Integer autoQueueMode;
    @Column(name = "enable_scripting")
    private Boolean enableScripting;
    @Column(name = "version_update_alert_sent")
    private String versionUpdateAlertSent;
    @OneToMany(mappedBy = "guildSpecification")
    private Set<AccessConfiguration> accessConfigurations = Sets.newHashSet();

    public GuildSpecification() {
    }

    public GuildSpecification(Guild guild) {
        guildId = guild.getId();
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

    public void setAccessConfigurations(Set<AccessConfiguration> accessConfigurations) {
        this.accessConfigurations = accessConfigurations;
    }

    public Guild getGuild(ShardManager shardManager) {
        return shardManager.getGuildById(getGuildId());
    }

    public Boolean isSendPlaybackNotification() {
        return sendPlaybackNotification;
    }

    public void setSendPlaybackNotification(Boolean sendPlaybackNotification) {
        this.sendPlaybackNotification = sendPlaybackNotification;
    }

    public Boolean isEnableAutoPause() {
        return enableAutoPause;
    }

    public void setEnableAutoPause(Boolean enableAutoPause) {
        this.enableAutoPause = enableAutoPause;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getDefaultSource() {
        return defaultSource;
    }

    public void setDefaultSource(String defaultSource) {
        this.defaultSource = defaultSource;
    }

    public String getDefaultListSource() {
        return defaultListSource;
    }

    public void setDefaultListSource(String defaultListSource) {
        this.defaultListSource = defaultListSource;
    }

    public Character getArgumentPrefix() {
        return argumentPrefix;
    }

    public void setArgumentPrefix(Character argumentPrefix) {
        this.argumentPrefix = argumentPrefix;
    }

    public Integer getTempMessageTimeout() {
        return tempMessageTimeout;
    }

    public void setTempMessageTimeout(Integer tempMessageTimeout) {
        this.tempMessageTimeout = tempMessageTimeout;
    }

    public String getDefaultTextChannelId() {
        return defaultTextChannelId;
    }

    public void setDefaultTextChannelId(String defaultTextChannelId) {
        this.defaultTextChannelId = defaultTextChannelId;
    }

    public Integer getDefaultVolume() {
        return defaultVolume;
    }

    public void setDefaultVolume(Integer volume) {
        this.defaultVolume = volume;
    }

    public Integer getAutoQueueMode() {
        return autoQueueMode;
    }

    public void setAutoQueueMode(Integer autoQueueMode) {
        this.autoQueueMode = autoQueueMode;
    }

    public Boolean isEnableScripting() {
        return enableScripting;
    }

    public void setEnableScripting(Boolean enableScripting) {
        this.enableScripting = enableScripting;
    }

    public String getVersionUpdateAlertSent() {
        return versionUpdateAlertSent;
    }

    public void setVersionUpdateAlertSent(String versionUpdateAlertSent) {
        this.versionUpdateAlertSent = versionUpdateAlertSent;
    }
}
