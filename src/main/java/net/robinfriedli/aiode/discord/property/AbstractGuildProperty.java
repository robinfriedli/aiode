package net.robinfriedli.aiode.discord.property;

import java.util.Optional;

import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;
import net.robinfriedli.aiode.exceptions.InvalidPropertyValueException;
import net.robinfriedli.aiode.function.HibernateInvoker;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.stringlist.StringList;

import static net.robinfriedli.jxp.queries.Conditions.*;

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
     *
     * @param value the value as entered by the user
     */
    public void set(String value) {
        set(value, ExecutionContext.Current.require().getGuildContext());
    }

    public void set(String value, GuildContext guildContext) {
        validateInput(value);
        StaticSessionProvider.consumeSession(session -> HibernateInvoker.create(session).invoke(() -> {
            GuildSpecification guildSpecification = guildContext.getSpecification(session);
            setValue(value, guildSpecification);
        }));
    }

    public abstract void setValue(String value, GuildSpecification guildSpecification);

    /**
     * @return the persisted value of the property if not null or default value. Mind that this implementation relies on this method
     * being called by a thread with a command context set up. Use {@link #get(GuildSpecification)} instead to
     * define the target GuildSpecification explicitly when this is not the case.
     */
    public Object get() {
        return get(ExecutionContext.Current.require().getGuildContext().getSpecification());
    }

    public <E> E get(Class<E> type) {
        return type.cast(get());
    }

    public Object get(GuildSpecification specification) {
        Object persistedValue = extractPersistedValue(specification);
        if (persistedValue != null) {
            return persistedValue;
        } else {
            return process(getDefaultValue());
        }
    }

    public <E> E get(Class<E> type, GuildSpecification specification) {
        Object persistedValue = extractPersistedValue(specification);
        if (persistedValue != null) {
            return type.cast(persistedValue);
        } else {
            return type.cast(process(getDefaultValue()));
        }
    }

    /**
     * Get the set value for the current context.
     */
    public Optional<Object> getSetValue() {
        return getSetValue(ExecutionContext.Current.require().getGuildContext().getSpecification());
    }

    /**
     * Return the persisted value, ignoring the default value, as optional
     */
    public Optional<Object> getSetValue(GuildSpecification guildSpecification) {
        return Optional.ofNullable(extractPersistedValue(guildSpecification));
    }

    public <E> Optional<E> getSetValue(Class<E> type) {
        return getSetValue(type, ExecutionContext.Current.require().getGuildContext().getSpecification());
    }

    public <E> Optional<E> getSetValue(Class<E> type, GuildSpecification guildSpecification) {
        return Optional.ofNullable(type.cast(extractPersistedValue(guildSpecification)));
    }

    public abstract Object extractPersistedValue(GuildSpecification guildSpecification);

    public String display(GuildSpecification guildSpecification) {
        Object persistedValue = extractPersistedValue(guildSpecification);
        if (persistedValue != null) {
            return String.valueOf(persistedValue);
        } else {
            return "Not Set";
        }
    }

    void validateInput(String value) {
        StringList acceptedValues = contribution.query(tagName("acceptedValue")).getResultStream().map(XmlElement::getTextContent).collect(StringList.collector());
        if (!acceptedValues.isEmpty()) {
            boolean valueAccepted = acceptedValues.contains(value, true);
            if (!valueAccepted) {
                String acceptedValuesString = contribution.getAcceptedValuesString(acceptedValues);
                throw new InvalidPropertyValueException(String.format("Unacceptable value '%s'.\nAcceptable values: %s", value, acceptedValuesString));
            }
        }
    }

}
