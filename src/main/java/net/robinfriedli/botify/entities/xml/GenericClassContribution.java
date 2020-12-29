package net.robinfriedli.botify.entities.xml;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import net.robinfriedli.botify.util.InjectorService;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public abstract class GenericClassContribution<E> extends AbstractXmlElement {

    // invoked by JXP
    @SuppressWarnings("unused")
    public GenericClassContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends E> getImplementationClass() {
        Class<? extends E> implementation;
        String className = getAttribute(defineClassAttribute()).getValue();
        try {
            implementation = (Class<E>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class " + className + " does not exist", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Could not cast class " + className + " to the target class", e);
        }

        return implementation;
    }

    @SuppressWarnings("unchecked")
    public E instantiate() {
        Class<? extends E> implementation = getImplementationClass();

        Constructor<?>[] constructors = implementation.getConstructors();
        if (constructors.length == 0) {
            throw new IllegalStateException("Class " + implementation + " does not have any public constructors");
        }

        Constructor<E> constructor = (Constructor<E>) constructors[0];
        int parameterCount = constructor.getParameterCount();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] constructorParameters = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.isAssignableFrom(getClass())) {
                constructorParameters[i] = this;
            } else {
                constructorParameters[i] = InjectorService.get(parameterType);
            }
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

    protected String defineClassAttribute() {
        return "implementation";
    }

}
