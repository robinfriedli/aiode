package net.robinfriedli.botify.discord.property.properties;

import net.robinfriedli.botify.discord.property.AbstractBoolProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;

public class EnableScriptingProperty extends AbstractBoolProperty {

    public EnableScriptingProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    protected void setBoolValue(boolean bool, GuildSpecification guildSpecification) {
        guildSpecification.setEnableScripting(bool);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.isEnableScripting();
    }

}
