package net.robinfriedli.botify.discord.property.properties;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import org.hibernate.Session;

/**
 * Property that defines the custom command prefix
 */
public class PrefixProperty extends AbstractGuildProperty {

    public static final String DEFAULT_FALLBACK = "$botify";

    public PrefixProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    /**
     * @return the prefix for a command based on the current context. Simply returns the prefix if set, else returns the
     * bot name plus a trailing whitespace if present or else "$botify " / the default fallback. This is meant to be used to format example commands.
     */
    public static String getEffectiveCommandStartForCurrentContext() {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
        return guildPropertyManager.getPropertyOptional("prefix")
            .flatMap(property -> property.getSetValue(String.class))
            .or(() -> guildPropertyManager.getPropertyOptional("botName")
                .flatMap(property -> property.getSetValue(String.class))
                .map(s -> s + " "))
            .orElse(DEFAULT_FALLBACK + " ");
    }

    public static String getForContext(GuildContext guildContext, Session session) {
        GuildSpecification specification = guildContext.getSpecification(session);
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
        return guildPropertyManager
            .getPropertyValueOptional("prefix", String.class, specification)
            .orElse(DEFAULT_FALLBACK);
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
