package net.robinfriedli.botify.discord.property.properties;

import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;

/**
 * Property that defines the default source to use for playlist search, "spotify", "youtube" or "local"
 */
public class DefaultListSourceProperty extends AbstractGuildProperty {

    public DefaultListSourceProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void doValidate(Object state) {
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
