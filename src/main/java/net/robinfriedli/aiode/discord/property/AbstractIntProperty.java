package net.robinfriedli.aiode.discord.property;

import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property extension for properties that have a boolean value.
 */
public abstract class AbstractIntProperty extends AbstractGuildProperty {

    public AbstractIntProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
    }

    @Override
    public Object process(String input) {
        return Integer.parseInt(input);
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        setIntValue((int) process(value), guildSpecification);
    }

    protected abstract void setIntValue(int integer, GuildSpecification guildSpecification);

}