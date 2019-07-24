package net.robinfriedli.botify.discord.properties;

import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;

public class PlaybackNotificationProperty extends AbstractBoolProperty {

    public PlaybackNotificationProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    void setBoolValue(boolean bool, GuildSpecification guildSpecification) {
        guildSpecification.setSendPlaybackNotification(bool);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.isSendPlaybackNotification();
    }
}