package net.robinfriedli.aiode.discord.property.properties;

import net.robinfriedli.aiode.discord.property.AbstractBoolProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

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
