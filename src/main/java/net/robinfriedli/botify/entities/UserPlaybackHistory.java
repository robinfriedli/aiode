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

import net.dv8tion.jda.api.entities.User;

/**
 * Entity that is created for each user currently in the voice channel when creating the {@link PlaybackHistory}
 */
@Entity
@Table(name = "user_playback_history")
public class UserPlaybackHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "user_name")
    private String userName;
    @Column(name = "user_id")
    private String userId;
    @ManyToOne
    @JoinColumn(name = "playback_history_pk", referencedColumnName = "pk", foreignKey = @ForeignKey(name = "fk_playback_history"))
    private PlaybackHistory playbackHistory;

    public UserPlaybackHistory() {
    }

    public UserPlaybackHistory(User user, PlaybackHistory playbackHistory) {
        userName = user.getName();
        userId = user.getId();
        this.playbackHistory = playbackHistory;
        playbackHistory.addUserPlaybackHistory(this);
    }

    public long getPk() {
        return pk;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public PlaybackHistory getPlaybackHistory() {
        return playbackHistory;
    }

    public void setPlaybackHistory(PlaybackHistory playbackHistory) {
        this.playbackHistory = playbackHistory;
    }
}
