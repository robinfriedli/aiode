package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "client_session")
public class ClientSession implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "session_id", unique = true, nullable = false)
    private UUID sessionId;
    @Column(name = "ip_address", nullable = false)
    private String ipAddress;
    @Column(name = "guild_id", nullable = false)
    private long guildId;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @Column(name = "text_channel_id", nullable = false)
    private long textChannelId;
    @Column(name = "last_refresh", nullable = false)
    private LocalDateTime lastRefresh;


    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public long getGuildId() {
        return guildId;
    }

    public void setGuildId(long guildId) {
        this.guildId = guildId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getTextChannelId() {
        return textChannelId;
    }

    public void setTextChannelId(long textChannelId) {
        this.textChannelId = textChannelId;
    }

    public LocalDateTime getLastRefresh() {
        return lastRefresh;
    }

    public void setLastRefresh(LocalDateTime lastRefresh) {
        this.lastRefresh = lastRefresh;
    }
}
