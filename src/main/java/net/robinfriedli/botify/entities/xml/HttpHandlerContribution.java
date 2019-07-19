package net.robinfriedli.botify.entities.xml;

import java.util.List;

import javax.annotation.Nullable;

import com.sun.net.httpserver.HttpHandler;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class HttpHandlerContribution extends GenericClassContribution<HttpHandler> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public HttpHandlerContribution(Element element, Context context) {
        super(element, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public HttpHandlerContribution(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("path").getValue();
    }

}
