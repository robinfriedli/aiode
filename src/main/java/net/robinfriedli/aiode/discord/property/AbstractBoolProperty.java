package net.robinfriedli.aiode.discord.property;

import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property extension for properties that have a boolean value.
 */
public abstract class AbstractBoolProperty extends AbstractGuildProperty {

    public AbstractBoolProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
    }

    @Override
    public Object process(String input) {
        return Boolean.parseBoolean(input);
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        setBoolValue((boolean) process(value), guildSpecification);
    }

    protected abstract void setBoolValue(boolean bool, GuildSpecification guildSpecification);

}
