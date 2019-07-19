package net.robinfriedli.botify.entities.xml;

import java.util.List;

import javax.annotation.Nullable;

import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class StartupTaskContribution extends GenericClassContribution<StartupTask> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public StartupTaskContribution(Element element, Context context) {
        super(element, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public StartupTaskContribution(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("implementation").getValue();
    }

}
