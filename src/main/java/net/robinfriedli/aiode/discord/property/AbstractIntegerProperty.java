package net.robinfriedli.aiode.discord.property;

import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property extension for properties that have a boolean value.
 */
public abstract class AbstractIntegerProperty extends AbstractGuildProperty {

    public AbstractIntegerProperty(GuildPropertyContribution contribution) {
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
        setIntegerValue((Integer) process(value), guildSpecification);
    }

    protected abstract void setIntegerValue(Integer integer, GuildSpecification guildSpecification);

}