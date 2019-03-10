package net.robinfriedli.botify.entities;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.robinfriedli.botify.command.CommandInterceptor;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class CommandInterceptorContribution extends AbstractXmlElement {

    public CommandInterceptorContribution(String tagName, Map<String, ?> attributeMap, List<XmlElement> subElements, String textContent, Context context) {
        super(tagName, attributeMap, subElements, textContent, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public CommandInterceptorContribution(Element element, Context context) {
        super(element, context);
    }

    @Nullable
    @Override
    public String getId() {
        return null;
    }

    @SuppressWarnings("unchecked")
    public CommandInterceptor instantiate() {
        String className = getAttribute("implementation").getValue();
        try {
            Class<CommandInterceptor> implementation = (Class<CommandInterceptor>) Class.forName(className);
            return implementation.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Interceptor " + className + " does not exist", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Interceptor " + className + " does not have an empty constructor", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Interceptor " + className + " does not implement CommandInterceptor", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access constructor of " + className, e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Cannot instantiate " + className, e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking constructor of " + className, e);
        }
    }

}
