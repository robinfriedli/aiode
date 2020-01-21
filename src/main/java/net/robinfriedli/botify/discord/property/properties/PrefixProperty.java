package net.robinfriedli.botify.discord.property.properties;

import java.util.Optional;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

/**
 * Property that defines the custom command prefix
 */
public class PrefixProperty extends AbstractGuildProperty {

    public PrefixProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    /**
     * @return the prefix for a command based on the current context. Simply returns the prefix if set, else returns the
     * bot name plus a trailing whitespace if present or else "$botify ". This is meant to be used to format example commands.
     */
    public static String getEffectiveCommandStartForCurrentContext() {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
        return Optional.ofNullable(guildPropertyManager.getProperty("prefix"))
            .flatMap((property -> property.getSetValue(String.class)))
            .or(() ->
                Optional.ofNullable(guildPropertyManager.getProperty("botName"))
                    .flatMap(property -> property.getSetValue(String.class))
                    .map(s -> s + " "))
            .orElse("$botify ");
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
