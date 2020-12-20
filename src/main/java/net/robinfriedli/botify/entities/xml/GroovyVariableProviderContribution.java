package net.robinfriedli.botify.entities.xml;

import net.robinfriedli.botify.scripting.GroovyVariableProvider;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public class GroovyVariableProviderContribution extends GenericClassContribution<GroovyVariableProvider> {

    public GroovyVariableProviderContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("implementation").getValue();
    }

}
