package net.robinfriedli.botify.discord.property.properties;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;

public class ArgumentPrefixProperty extends AbstractGuildProperty {

    public static final char DEFAULT = '$';

    public ArgumentPrefixProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    /**
     * Static access to the argument prefix for the current prefix. May only be used by threads with a CommandContext
     * set up, such as CommandExecutionThreads.
     *
     * @return the set argument prefix for the current guild
     */
    public static char getForCurrentContext() {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
        AbstractGuildProperty argumentPrefixProperty = guildPropertyManager.getProperty("argumentPrefix");
        char argumentPrefix;
        if (argumentPrefixProperty != null) {
            argumentPrefix = (char) argumentPrefixProperty.get();
        } else {
            argumentPrefix = DEFAULT;
        }

        return argumentPrefix;
    }

    @Override
    public void validate(Object state) {
    }

    @Override
    public Object process(String input) {
        if (input.length() != 1) {
            throw new InvalidPropertyValueException("Length of argument prefix should be exactly 1");
        }
        return input.charAt(0);
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        guildSpecification.setArgumentPrefix((Character) process(value));
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getArgumentPrefix();
    }

}
