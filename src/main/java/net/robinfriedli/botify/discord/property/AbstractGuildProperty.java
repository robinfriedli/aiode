package net.robinfriedli.botify.discord.property;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.util.StaticSessionProvider;

/**
 * Represents a property persisted as column on the {@link GuildSpecification class}. Offers default values and validation
 * for implemented properties.
 */
public abstract class AbstractGuildProperty {

    private final GuildPropertyContribution contribution;

    public AbstractGuildProperty(GuildPropertyContribution contribution) {
        this.contribution = contribution;
    }

    public abstract void validate(Object state);

    public abstract Object process(String input);

    public String getDefaultValue() {
        return contribution.getDefaultValue();
    }

    public String getProperty() {
        return contribution.getProperty();
    }

    public String getName() {
        return contribution.getName();
    }

    public GuildPropertyContribution getContribution() {
        return contribution;
    }

    /**
     * set the value of the property on the GuildSpecification. Mind that this implementation relies on this method
     * being called by a thread with a command context set up. Use {@link #set(String, GuildContext)} instead to
     * define the target GuildContext explicitly when this is not the case.
     */
    public void set(String value) {
        set(value, CommandContext.Current.require().getGuildContext());
    }

    public void set(String value, GuildContext guildContext) {
        StaticSessionProvider.invokeWithSession(session -> {
            guildContext.getInvoker().invoke(session, () -> {
                GuildSpecification guildSpecification = guildContext.getSpecification(session);
                setValue(value, guildSpecification);
            });
        });
    }

    public abstract void setValue(String value, GuildSpecification guildSpecification);

    /**
     * @return the persisted value of the property if not null or default value. Mind that this implementation relies on this method
     * being called by a thread with a command context set up. Use {@link #get(GuildSpecification)} instead to
     * define the target GuildSpecification explicitly when this is not the case.
     */
    public Object get() {
        return get(CommandContext.Current.require().getGuildContext().getSpecification());
    }

    public Object get(GuildSpecification specification) {
        Object persistedValue = extractPersistedValue(specification);
        if (persistedValue != null) {
            return persistedValue;
        } else {
            return process(getDefaultValue());
        }
    }

    public abstract Object extractPersistedValue(GuildSpecification guildSpecification);

}
