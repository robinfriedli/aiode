package net.robinfriedli.aiode.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table that holds a single row that describes the current usage of the daily YouTube API quota. This number is a
 * calculated approximate of the actual Quota usage using value from the
 * <a href="https://developers.google.com/youtube/v3/determine_quota_cost">YouTube Data API (v3) - Quota Calculator</a>
 * and is not guaranteed to represent the actual Quota usage. From Google: "To use the tool, select the appropriate
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
    @Column(name = "last_updated")
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
