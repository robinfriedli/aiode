package net.robinfriedli.aiode.discord.property.properties;

import net.robinfriedli.aiode.discord.property.AbstractIntegerProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property that toggles "Now playing..." messages
 */
public class DefaultVolumeProperty extends AbstractIntegerProperty {

    public DefaultVolumeProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public Object process(String input) {
        int volume = Integer.parseInt(input);
        if (volume < 1 || volume > 200) throw new IllegalArgumentException("Volume must be between 1 and 200");
        return volume;
    }

    @Override
    protected void setIntegerValue(Integer volume, GuildSpecification guildSpecification) {
        guildSpecification.setDefaultVolume(volume);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getDefaultVolume();
    }
}