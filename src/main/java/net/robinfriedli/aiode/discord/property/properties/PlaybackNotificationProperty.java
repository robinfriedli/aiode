package net.robinfriedli.aiode.discord.property.properties;

import net.robinfriedli.aiode.discord.property.AbstractBoolProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property that toggles "Now playing..." messages
 */
public class PlaybackNotificationProperty extends AbstractBoolProperty {

    public PlaybackNotificationProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    protected void setBoolValue(boolean bool, GuildSpecification guildSpecification) {
        guildSpecification.setSendPlaybackNotification(bool);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.isSendPlaybackNotification();
    }
}
