package net.robinfriedli.aiode.discord.property;

import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;
import net.robinfriedli.aiode.exceptions.InvalidPropertyValueException;

/**
 * Property extension for properties that have an integer value.
 */
public abstract class AbstractIntegerProperty extends AbstractGuildProperty {

    public AbstractIntegerProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public Object process(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InvalidPropertyValueException(String.format("'%s' is not an integer", input));
        }
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        setIntegerValue((Integer) process(value), guildSpecification);
    }

    protected abstract void setIntegerValue(Integer integer, GuildSpecification guildSpecification);

}
