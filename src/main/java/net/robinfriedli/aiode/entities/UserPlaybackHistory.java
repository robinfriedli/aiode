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
import net.dv8tion.jda.api.entities.User;

/**
 * Entity that is created for each user currently in the voice channel when creating the {@link PlaybackHistory}
 */
@Entity
@Table(name = "user_playback_history", indexes = {
    @Index(name = "user_playback_history_user_id_idx", columnList = "user_id")
})
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
    @Column(name = "playback_history_pk", insertable = false, updatable = false)
    private long playbackHistoryPk;

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

    public long getPlaybackHistoryPk() {
        return playbackHistoryPk;
    }

    public void setPlaybackHistoryPk(long playbackHistoryPk) {
        this.playbackHistoryPk = playbackHistoryPk;
    }
}
