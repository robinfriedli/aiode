package net.robinfriedli.aiode.discord.property.properties;

import net.robinfriedli.aiode.discord.property.AbstractBoolProperty;
import net.robinfriedli.aiode.discord.property.AbstractIntProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property that toggles "Now playing..." messages
 */
public class VolumeProperty extends AbstractIntProperty {

    public VolumeProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    protected void setIntValue(int volume, GuildSpecification guildSpecification) {
        guildSpecification.setVolume(volume);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getVolume();
    }
}