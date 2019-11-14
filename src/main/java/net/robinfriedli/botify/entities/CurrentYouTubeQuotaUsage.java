package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Table that holds a single row that describes the current usage of the daily YouTube API quota. This number is a
 * calculated approximate of the actual Quota usage using value from the
 * <a href="https://developers.google.com/youtube/v3/determine_quota_cost">YouTube Data API (v3) - Quota Calculator</a>
 * and is nit guaranteed to represent the actual Quota usage. From Google: "To use the tool, select the appropriate
 * resource, method, and part parameter values for your request, and the approximate quota cost will display in the table.
 * Please remember that quota costs can change without warning, and the values shown here may not be exact."
 */
@Entity
@Table(name = "current_youtube_quota_usage")
public class CurrentYouTubeQuotaUsage implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column
    private int quota;
    @Column
    private LocalDateTime lastUpdated;

    public long getPk() {
        return pk;
    }

    public int getQuota() {
        return quota;
    }

    public void setQuota(int quota) {
        this.quota = quota;
        lastUpdated = LocalDateTime.now();
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
