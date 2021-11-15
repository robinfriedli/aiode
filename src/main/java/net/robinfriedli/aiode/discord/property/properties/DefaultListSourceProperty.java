package net.robinfriedli.aiode.discord.property.properties;

import net.robinfriedli.aiode.discord.property.AbstractGuildProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property that defines the default source to use for playlist search, "spotify", "youtube" or "local"
 */
public class DefaultListSourceProperty extends AbstractGuildProperty {

    public DefaultListSourceProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
    }

    @Override
    public Object process(String input) {
        return input;
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        guildSpecification.setDefaultListSource(value.toUpperCase());
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getDefaultListSource();
    }

}
