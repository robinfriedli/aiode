package net.robinfriedli.aiode.entities.xml;

import javax.annotation.Nullable;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class EmbedDocumentContribution extends AbstractXmlElement {

    private final GroovyShell groovyShell = new GroovyShell();

    // invoked by JXP
    @SuppressWarnings("unused")
    public EmbedDocumentContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("name").getValue();
    }

    public String getName() {
        return getId();
    }

    public EmbedBuilder buildEmbed() {
        Aiode aiode = Aiode.get();
        groovyShell.setProperty("aiode", aiode);
        groovyShell.setProperty("properties", aiode.getSpringPropertiesConfig());

        String title = getAttribute("title").getValue();
        String description = getAttribute("description").getValue();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(title);
        embedBuilder.setDescription(description);

        for (XmlElement field : getSubElements()) {
            if (field.hasAttribute("displayPredicate")) {
                Object displayPredicateResult = groovyShell.evaluate(field.getAttribute("displayPredicate").getValue());

                if (!(displayPredicateResult instanceof Boolean)) {
                    throw new IllegalStateException("displayPredicate did not return a boolean");
                }

                if (!((boolean) displayPredicateResult)) {
                    continue;
                }
            }

            String fieldTitle = field.getAttribute("title").getValue();
            boolean inline = field.getAttribute("inline").getBool();
            embedBuilder.addField(fieldTitle, field.getTextContent(), inline);
        }

        return embedBuilder;
    }

}
