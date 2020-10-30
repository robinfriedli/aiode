package net.robinfriedli.botify.discord.property.properties;

import java.util.Objects;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.parser.CommandParser;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;

/**
 * Property that defines the argument prefix
 */
public class ArgumentPrefixProperty extends AbstractGuildProperty {

    public static final char DEFAULT_FALLBACK = '$';

    public ArgumentPrefixProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    /**
     * Static access to the argument prefix for the current prefix. May only be used by threads with a CommandContext
     * set up, such as CommandExecutionThreads.
     *
     * @return the set argument prefix for the current guild
     */
    public static Config getForCurrentContext() {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();

        return guildPropertyManager.getPropertyOptional("argumentPrefix")
            .map(p -> {
                String defaultValueString = p.getDefaultValue();
                char[] chars = defaultValueString.toCharArray();
                if (chars.length != 1) {
                    throw new IllegalStateException("Default value for argumentPrefix is not a single char");
                }

                char defaultArgumentPrefix = chars[0];
                char argumentPrefix = p.get(Character.class);

                return new Config(argumentPrefix, defaultArgumentPrefix);
            }).orElseGet(() -> new Config(DEFAULT_FALLBACK, DEFAULT_FALLBACK));
    }

    @Override
    public void validate(Object state) {
        Character input = (Character) state;
        if (!Objects.equals(input, DEFAULT_FALLBACK) && CommandParser.META.contains(input)) {
            throw new InvalidPropertyValueException("Cannot set argument prefix to reserved meta character " + input);
        }
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

    public static class Config {

        private final char argumentPrefix;
        private final char defaultArgumentPrefix;

        public Config(char argumentPrefix, char defaultArgumentPrefix) {
            this.argumentPrefix = argumentPrefix;
            this.defaultArgumentPrefix = defaultArgumentPrefix;
        }

        public char getArgumentPrefix() {
            return argumentPrefix;
        }

        public char getDefaultArgumentPrefix() {
            return defaultArgumentPrefix;
        }
    }

}
