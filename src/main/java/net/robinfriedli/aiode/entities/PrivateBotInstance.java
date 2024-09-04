package net.robinfriedli.aiode.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "private_bot_instance")
public class PrivateBotInstance {

    @Id
    @Column(name = "identifier", nullable = false, unique = true, updatable = false)
    private String identifier;
    @Column(name = "server_limit", nullable = false)
    private int serverLimit;
    @Column(name = "invite_link", nullable = false)
    private String inviteLink;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public int getServerLimit() {
        return serverLimit;
    }

    public void setServerLimit(int serverLimit) {
        this.serverLimit = serverLimit;
    }

    public String getInviteLink() {
        return inviteLink;
    }

    public void setInviteLink(String inviteLink) {
        this.inviteLink = inviteLink;
    }
}
