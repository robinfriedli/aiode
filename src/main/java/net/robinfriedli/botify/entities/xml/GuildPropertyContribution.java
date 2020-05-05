package net.robinfriedli.botify.entities.xml;

import javax.annotation.Nullable;

import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class GuildPropertyContribution extends GenericClassContribution<AbstractGuildProperty> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public GuildPropertyContribution(Element element, NodeList childNodes, Context context) {
        super(element, childNodes, context);
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

    public String getDefaultValue() {
        return getAttribute("defaultValue").getValue();
    }

    public String getUpdateMessage() {
        return getAttribute("updateMessage").getValue();
    }

}
