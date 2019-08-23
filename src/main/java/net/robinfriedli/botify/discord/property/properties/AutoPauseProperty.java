package net.robinfriedli.botify.discord.property.properties;

import net.robinfriedli.botify.discord.property.AbstractBoolProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;

public class AutoPauseProperty extends AbstractBoolProperty {

    public AutoPauseProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    protected void setBoolValue(boolean bool, GuildSpecification guildSpecification) {
        guildSpecification.setEnableAutoPause(bool);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.isEnableAutoPause();
    }
}
