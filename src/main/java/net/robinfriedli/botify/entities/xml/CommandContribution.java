package net.robinfriedli.botify.entities.xml;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.annotation.Nullable;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class CommandContribution extends CommandHierarchyNode {

    // invoked by JXP
    @SuppressWarnings("unused")
    public CommandContribution(Element element, Context context) {
        super(element, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public CommandContribution(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getIdentifier();
    }

    public String getIdentifier() {
        return getAttribute("identifier").getValue();
    }

    public AbstractCommand instantiate(CommandManager commandManager, CommandContext commandContext, String commandBody) {
        String identifier = getIdentifier();
        boolean requiresInput = getAttribute("requiresInput").getBool();
        String description = getAttribute("description").getValue();
        AbstractCommand.Category category = AbstractCommand.Category.valueOf(getAttribute("category").getValue());
        Class<? extends AbstractCommand> commandClass = getImplementationClass();

        try {
            Constructor<? extends AbstractCommand> constructor =
                commandClass.getConstructor(CommandContribution.class, CommandContext.class, CommandManager.class, String.class, boolean.class, String.class, String.class, AbstractCommand.Category.class);
            return constructor.newInstance(this, commandContext, commandManager, commandBody, requiresInput, identifier, description, category);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InvalidCommandException) {
                throw (InvalidCommandException) cause;
            }
            throw new RuntimeException("Exception while invoking constructor of " + commandClass.getSimpleName(), e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Class " + commandClass.getSimpleName() +
                " does not have a constructor matching CommandContext, CommandManager, String, String", e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Command class " + commandClass.getSimpleName() + " could not be instantiated.", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access constructor of " + commandClass.getSimpleName(), e);
        }
    }

    public static class AbstractCommandContribution extends CommandHierarchyNode {

        @SuppressWarnings("unused")
        public AbstractCommandContribution(Element element, Context context) {
            super(element, context);
        }

        @SuppressWarnings("unused")
        public AbstractCommandContribution(Element element, List<XmlElement> subElements, Context context) {
            super(element, subElements, context);
        }

        @Nullable
        @Override
        public String getId() {
            return getAttribute("class").getValue();
        }

        @Override
        protected String defineClassAttribute() {
            return "class";
        }
    }

}
