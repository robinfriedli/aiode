package net.robinfriedli.aiode.entities.xml;

import javax.annotation.Nullable;

import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class StartupTaskContribution extends GenericClassContribution<StartupTask> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public StartupTaskContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("implementation").getValue();
    }

}
