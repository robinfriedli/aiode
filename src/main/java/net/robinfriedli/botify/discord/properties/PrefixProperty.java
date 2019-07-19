package net.robinfriedli.botify.discord.properties;

import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class PrefixProperty extends AbstractGuildProperty {

    public PrefixProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
        String input = (String) state;
        if (input.length() < 1 || input.length() > 5) {
            throw new InvalidCommandException("Length should be 1 - 5 characters");
        }
    }

    @Override
    public Object process(String input) {
        return input;
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        guildSpecification.setPrefix(value);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getPrefix();
    }
}
