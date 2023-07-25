package net.robinfriedli.aiode.discord.property.properties;

import net.robinfriedli.aiode.discord.property.AbstractGuildProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;
import net.robinfriedli.aiode.exceptions.InvalidPropertyValueException;

public class AutoQueueModeProperty extends AbstractGuildProperty {

    public AutoQueueModeProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
    }

    @Override
    public Object process(String input) {
        String trimmedInput = input.trim();
        if (trimmedInput.equalsIgnoreCase("off")) {
            return 0;
        } else if (trimmedInput.equalsIgnoreCase("queue next")) {
            return 1;
        } else if (trimmedInput.equalsIgnoreCase("queue last")) {
            return 2;
        } else {
            throw new InvalidPropertyValueException("Invalid auto queue mode");
        }
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        guildSpecification.setAutoQueueMode((Integer) process(value));
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getAutoQueueMode();
    }

    @Override
    public String display(GuildSpecification guildSpecification) {
        Object extractPersistedValue = extractPersistedValue(guildSpecification);
        if (extractPersistedValue instanceof Integer intValue) {
            if (intValue == 0) {
                return "off";
            } else if (intValue == 1) {
                return "queue next";
            } else if (intValue == 2) {
                return "queue last";
            } else {
                throw new IllegalStateException("Unknown auto queue mode: " + intValue);
            }
        } else {
            return "Not Set";
        }
    }
}
