package net.robinfriedli.aiode.entities.xml;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.robinfriedli.aiode.command.argument.ArgumentContributionDelegate;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Element;

import javax.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static net.robinfriedli.jxp.queries.Conditions.tagName;

public class ArgumentContribution extends AbstractXmlElement implements ArgumentContributionDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArgumentContribution.class);

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

    public List<XmlElement> getExcludedArguments() {
        return query(tagName("excludes")).collect(Collectors.toList());
    }

    public List<XmlElement> getRequiredArguments() {
        return query(tagName("requires")).collect(Collectors.toList());
    }

    public List<XmlElement> getRules() {
        return query(tagName("rule")).collect(Collectors.toList());
    }

    public List<XmlElement> getValueChecks() {
        return query(tagName("valueCheck")).collect(Collectors.toList());
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

        return Boolean.class;
    }

    public OptionType getOptionType() {
        Class<?> valueType = getValueType();

        if (String.class.isAssignableFrom(valueType)) {
            return OptionType.STRING;
        } else if (Integer.class.isAssignableFrom(valueType)) {
            return OptionType.INTEGER;
        } else if (Boolean.class.isAssignableFrom(valueType)) {
            return OptionType.BOOLEAN;
        } else if (Number.class.isAssignableFrom(valueType)) {
            return OptionType.NUMBER;
        }

        LOGGER.warn(String.format(
            "Value type %s of argument %s cannot be converted to OptionType, falling back to OptionType.STRING",
            valueType,
            getId()
        ));
        return OptionType.STRING;
    }

    public boolean requiresValue() {
        return getAttribute("requiresValue").getBool();
    }

    public boolean requiresInput() {
        return getAttribute("requiresInput").getBool();
    }

    @Override
    public ArgumentContribution unwrapArgumentContribution() {
        return this;
    }
}
