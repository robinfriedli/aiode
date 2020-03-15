package net.robinfriedli.botify.discord.property.properties;

import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;

/**
 * Property that defines the time after which to delete temporary messages in seconds. 0 means no timeout.
 */
public class TempMessageTimeoutProperty extends AbstractGuildProperty {

    public static final int DEFAULT_FALLBACK = 20;

    public TempMessageTimeoutProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void doValidate(Object state) {
        int timeoutSeconds = (int) state;

        if (timeoutSeconds < 0 || timeoutSeconds > 300) {
            throw new InvalidPropertyValueException("Expected a value between 0 and 300 seconds. If you want to disable the timeout, set it to 0.");
        }
    }

    @Override
    public Object process(String input) {
        int timeoutSeconds;

        try {
            timeoutSeconds = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InvalidPropertyValueException(String.format("'%s' is not an integer", input));
        }

        return timeoutSeconds;
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        guildSpecification.setTempMessageTimeout((Integer) process(value));
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getTempMessageTimeout();
    }
}
