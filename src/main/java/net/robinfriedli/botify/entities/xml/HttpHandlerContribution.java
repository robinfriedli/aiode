package net.robinfriedli.botify.entities.xml;

import javax.annotation.Nullable;

import com.sun.net.httpserver.HttpHandler;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class HttpHandlerContribution extends GenericClassContribution<HttpHandler> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public HttpHandlerContribution(Element element, NodeList childNodes, Context context) {
        super(element, childNodes, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("path").getValue();
    }

}
