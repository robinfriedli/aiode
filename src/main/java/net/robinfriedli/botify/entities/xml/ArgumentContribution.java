package net.robinfriedli.botify.entities.xml;

import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class ArgumentContribution extends AbstractXmlElement {

    @SuppressWarnings("unused")
    public ArgumentContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        String parentId = getParent() != null ? getParent().getId() : null;
        return parentId + "$" + getIdentifier();
    }

    public String getIdentifier() {
        return getAttribute("identifier").getValue();
    }

    public String getDescription() {
        return getAttribute("description").getValue();
    }

    public Set<XmlElement> getExcludedArguments() {
        return query(tagName("excludes")).collect(Collectors.toSet());
    }

    public Set<XmlElement> getRequiredArguments() {
        return query(tagName("requires")).collect(Collectors.toSet());
    }

    public Set<XmlElement> getRules() {
        return query(tagName("rule")).collect(Collectors.toSet());
    }

    public Set<XmlElement> getValueChecks() {
        return query(tagName("valueCheck")).collect(Collectors.toSet());
    }

    public Class<?> getValueType() {
        if (hasAttribute("valueType")) {
            String className = getAttribute("valueType").getValue();
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("No such class " + className);
            }
        }

        return String.class;
    }

}
