package net.robinfriedli.botify.entities.xml;

import javax.annotation.Nullable;

import groovy.lang.GroovyShell;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.stringlist.StringList;
import org.w3c.dom.Element;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class GuildPropertyContribution extends GenericClassContribution<AbstractGuildProperty> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public GuildPropertyContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getProperty();
    }

    public String getProperty() {
        return getAttribute("property").getValue();
    }

    public String getName() {
        return getAttribute("name").getValue();
    }

    public String getDescription() {
        return getAttribute("description").getValue();
    }

    public String getAcceptedValuesString() {
        StringList acceptedValues = query(tagName("acceptedValue")).getResultStream().map(XmlElement::getTextContent).collect(StringList.collector());
        return getAcceptedValuesString(acceptedValues);
    }

    public String getAcceptedValuesString(StringList acceptedValues) {
        StringBuilder acceptedValuesStringBuilder = new StringBuilder();
        for (int i = 0; i < acceptedValues.size(); i++) {
            String acceptedValue = acceptedValues.get(i);
            acceptedValuesStringBuilder.append(acceptedValue);

            if (i < acceptedValues.size() - 2) {
                acceptedValuesStringBuilder.append(", ");
            } else if (i < acceptedValues.size() - 1) {
                acceptedValuesStringBuilder.append(" or ");
            }
        }

        return acceptedValuesStringBuilder.toString();
    }

    public String getDefaultValue() {
        return getAttribute("defaultValue").getValue();
    }

    public String getUpdateMessage(Object value) {
        if (hasAttribute("updateMessage")) {
            String updateMessage = getAttribute("updateMessage").getValue();
            return String.format(updateMessage, value);
        } else if (hasAttribute("updateMessageScript")) {
            GroovyShell groovyShell = new GroovyShell();
            ExecutionContext executionContext = ExecutionContext.Current.get();
            if (executionContext != null) {
                groovyShell.setVariable("guild", executionContext.getGuild());
            }
            groovyShell.setVariable("value", value);
            return (String) groovyShell.evaluate(getAttribute("updateMessageScript").getValue());
        } else {
            return String.format("Set property '%s' to '%s'", getName(), value);
        }
    }

}
