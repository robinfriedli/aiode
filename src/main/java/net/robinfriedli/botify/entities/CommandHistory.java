package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "command_history")
public class CommandHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    @Column(name = "start_millis", nullable = false)
    private long startMillis;
    @Column(name = "command_identifier")
    private String commandIdentifier;
    @Column(name = "is_widget", nullable = false)
    private boolean isWidget;
    @Column(name = "command_context_id")
    private String commandContextId;
    @Column(name = "command_body", length = 2000)
    private String commandBody;
    @Column(name = "input", length = 2000)
    private String input;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @Column(name = "user_name")
    private String user;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "completed_successfully", nullable = false)
    private boolean completedSuccessfully;
    // if the command has failed by explicitly calling setFailed()
    @Column(name = "failed_manually", nullable = false)
    private boolean failedManually;
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
    @Column(name = "unexpected_exception", nullable = false)
    private boolean unexpectedException;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    @Column(name = "aborted", nullable = false)
    private boolean aborted;

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getCommandIdentifier() {
        return commandIdentifier;
    }

    public void setCommandIdentifier(String commandIdentifier) {
        this.commandIdentifier = commandIdentifier;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getGuild() {
        return guild;
    }

    public void setGuild(String guild) {
        this.guild = guild;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isCompletedSuccessfully() {
        return completedSuccessfully;
    }

    public void setCompletedSuccessfully(boolean completedSuccessfully) {
        this.completedSuccessfully = completedSuccessfully;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isUnexpectedException() {
        return unexpectedException;
    }

    public void setUnexpectedException(boolean unexpectedException) {
        this.unexpectedException = unexpectedException;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isFailedManually() {
        return failedManually;
    }

    public void setFailedManually(boolean failedManually) {
        this.failedManually = failedManually;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public String getCommandBody() {
        return commandBody;
    }

    public void setCommandBody(String commandBody) {
        this.commandBody = commandBody;
    }

    public String getCommandContextId() {
        return commandContextId;
    }

    public void setCommandContextId(String commandContextId) {
        this.commandContextId = commandContextId;
    }

    public boolean isWidget() {
        return isWidget;
    }

    public void setWidget(boolean widget) {
        isWidget = widget;
    }

    public boolean isAborted() {
        return aborted;
    }

    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }
}
