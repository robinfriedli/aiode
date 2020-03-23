package net.robinfriedli.botify.discord.property.properties;

import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;

/**
 * Property that defines the default source to use for track search, "spotify" or "youtube"
 */
public class DefaultSourceProperty extends AbstractGuildProperty {

    public DefaultSourceProperty(GuildPropertyContribution contribution) {
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
        guildSpecification.setDefaultSource(value.toUpperCase());
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getDefaultSource();
    }
}
