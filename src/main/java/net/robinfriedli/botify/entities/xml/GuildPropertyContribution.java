package net.robinfriedli.botify.entities.xml;

import java.util.List;

import javax.annotation.Nullable;

import net.robinfriedli.botify.discord.properties.AbstractGuildProperty;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class GuildPropertyContribution extends GenericClassContribution<AbstractGuildProperty> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public GuildPropertyContribution(Element element, Context context) {
        super(element, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public GuildPropertyContribution(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getName();
    }

    public String getProperty() {
        return getAttribute("property").getValue();
    }

    public String getName() {
        return getAttribute("name").getValue();
    }

    public String getDefaultValue() {
        return getAttribute("defaultValue").getValue();
    }

    public String getUpdateMessage() {
        return getAttribute("updateMessage").getValue();
    }

}
