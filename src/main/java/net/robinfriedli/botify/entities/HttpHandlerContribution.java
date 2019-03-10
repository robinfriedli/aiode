package net.robinfriedli.botify.entities;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.sun.net.httpserver.HttpHandler;
import net.robinfriedli.botify.util.ParameterContainer;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class HttpHandlerContribution extends AbstractXmlElement {

    public HttpHandlerContribution(String tagName, Map<String, ?> attributeMap, List<XmlElement> subElements, String textContent, Context context) {
        super(tagName, attributeMap, subElements, textContent, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public HttpHandlerContribution(Element element, Context context) {
        super(element, context);
    }

    @Nullable
    @Override
    public String getId() {
        return null;
    }

    @SuppressWarnings("unchecked")
    public HttpHandler instantiate(ParameterContainer parameterContainer) {
        Class<HttpHandler> implementation;
        String className = getAttribute("implementation").getValue();
        try {
            implementation = (Class<HttpHandler>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class " + className + " does not exist", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Class " + className + " does not implement HttpHandler", e);
        }

        Constructor<?>[] constructors = implementation.getConstructors();
        if (constructors.length == 0) {
            throw new IllegalStateException("Class " + className + " does not have any public constructors");
        }

        Constructor<HttpHandler> constructor = (Constructor<HttpHandler>) constructors[0];
        int parameterCount = constructor.getParameterCount();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] constructorParameters = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            constructorParameters[i] = parameterContainer.get(parameterTypes[i]);
        }

        try {
            return constructor.newInstance(constructorParameters);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access constructor " + constructor.toString(), e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Constructor " + constructor.toString() + " cannot be instantiated", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking constructor " + constructor.toString(), e);
        }
    }

}
