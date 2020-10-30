package net.robinfriedli.botify.entities.xml;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class CommandInterceptorContribution extends AbstractXmlElement {

    public CommandInterceptorContribution(Map<String, ?> attributeMap, List<XmlElement> subElements) {
        super("commandInterceptor", attributeMap, subElements);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public CommandInterceptorContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("implementation").getValue();
    }

    @SuppressWarnings("unchecked")
    public Class<? extends AbstractChainableCommandInterceptor> getImplementationClass() {
        String className = getAttribute("implementation").getValue();
        try {
            return (Class<AbstractChainableCommandInterceptor>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Interceptor " + className + " does not exist", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Interceptor " + className + " does not extend AbstractChainableCommandInterceptor", e);
        }
    }

    /**
     * Decide whether or not a certain exception should be thrown, thus interrupting the command execution, according
     * to this CommandInterceptorContribution's configuration
     */
    @SuppressWarnings("unchecked")
    public boolean throwException(Throwable e) {
        Logger logger = LoggerFactory.getLogger(getClass());
        try {
            List<Class<Throwable>> interruptingExceptions = query(tagName("interruptingException"))
                .collect()
                .stream()
                .map(xmlElement -> {
                    try {
                        return (Class<Throwable>) Class.forName(xmlElement.getAttribute("class").getValue());
                    } catch (ClassNotFoundException e1) {
                        throw new RuntimeException(e1);
                    }
                })
                .collect(Collectors.toList());

            return interruptingExceptions.stream().anyMatch(clazz -> clazz.isAssignableFrom(e.getClass()));
        } catch (Exception e2) {
            logger.error("Exception while handling interceptor exception", e2);
        }

        return false;
    }

}
