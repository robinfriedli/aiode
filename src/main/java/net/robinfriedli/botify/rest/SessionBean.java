package net.robinfriedli.botify.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public class SessionBean {

    private final SnowflakeBean user;
    private final SnowflakeBean guild;
    private final SnowflakeBean textChannel;
    private final String sessionId;

    public SessionBean(SnowflakeBean user, SnowflakeBean guild, SnowflakeBean textChannel, String sessionId) {
        this.user = user;
        this.guild = guild;
        this.textChannel = textChannel;
        this.sessionId = sessionId;
    }

    public static SessionBean create(long userId, String userName, long guildId, String guildName, long textChannelId, String textChannelName, String sessionId) {
        return new SessionBean(new SnowflakeBean(userId, userName), new SnowflakeBean(guildId, guildName), new SnowflakeBean(textChannelId, textChannelName), sessionId);
    }

    @JsonProperty("user")
    public SnowflakeBean getUser() {
        return user;
    }

    @JsonProperty("guild")
    public SnowflakeBean getGuild() {
        return guild;
    }

    @JsonProperty("text_channel")
    public SnowflakeBean getTextChannel() {
        return textChannel;
    }

    @JsonProperty("session_id")
    public String getSessionId() {
        return sessionId;
    }

    public static class SnowflakeBean {

        private final long id;
        private final String name;

        public SnowflakeBean(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @JsonProperty("id")
        public long getId() {
            return id;
        }

        @JsonProperty("name")
        public String getName() {
            return name;
        }
    }

}
