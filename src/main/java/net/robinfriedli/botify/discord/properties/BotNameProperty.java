package net.robinfriedli.botify.discord.properties;

import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;

public class BotNameProperty extends AbstractGuildProperty {

    public BotNameProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
        String input = (String) state;
        if (input.length() < 1 || input.length() > 20) {
            throw new InvalidPropertyValueException("Length should be 1 - 20 characters");
        }
    }

    @Override
    public Object process(String input) {
        return input;
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        guildSpecification.setBotName(value);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getBotName();
    }
}
