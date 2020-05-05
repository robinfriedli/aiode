package net.robinfriedli.botify.entities.xml;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class EmbedDocumentContribution extends AbstractXmlElement {

    // invoked by JXP
    @SuppressWarnings("unused")
    public EmbedDocumentContribution(Element element, NodeList childNodes, Context context) {
        super(element, childNodes, context);
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
        String title = getAttribute("title").getValue();
        String description = getAttribute("description").getValue();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(title);
        embedBuilder.setDescription(description);

        for (XmlElement field : getSubElements()) {
            String fieldTitle = field.getAttribute("title").getValue();
            boolean inline = field.getAttribute("inline").getBool();
            embedBuilder.addField(fieldTitle, field.getTextContent(), inline);
        }

        return embedBuilder;
    }

}
