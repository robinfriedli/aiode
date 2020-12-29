package net.robinfriedli.botify.entities.xml;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.command.argument.CommandArgument;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class CommandContribution extends CommandHierarchyNode<CommandArgument> implements PermissionTarget {

    // invoked by JXP
    @SuppressWarnings("unused")
    public CommandContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Override
    protected CommandArgument transformArgumentContribution(ArgumentContribution argumentContribution) {
        return new CommandArgument(this, argumentContribution);
    }

    @Nullable
    @Override
    public String getId() {
        return getIdentifier();
    }

    public String getIdentifier() {
        return getAttribute("identifier").getValue();
    }

    @Override
    public String getPermissionTargetIdentifier() {
        return getIdentifier();
    }

    @Override
    public TargetType getPermissionTargetType() {
        return TargetType.COMMAND;
    }

    @Nullable
    @Override
    public PermissionTarget getChildTarget(String identifier) {
        return getArgument(identifier);
    }

    @Override
    public Set<? extends PermissionTarget> getChildren() {
        return new HashSet<>(getArguments().values());
    }

    @Nullable
    @Override
    public PermissionTarget getParentTarget() {
        return null;
    }

    @Override
    public PermissionTypeCategory getPermissionTypeCategory() {
        return getCategory();
    }

    public AbstractCommand.Category getCategory() {
        return AbstractCommand.Category.valueOf(getAttribute("category").getValue());
    }

    public boolean isDisableScriptInterceptors() {
        return getAttribute("disableScriptInterceptors").getBool();
    }

    public AbstractCommand instantiate(CommandManager commandManager, CommandContext commandContext, String commandBody) {
        String identifier = getIdentifier();
        boolean requiresInput = getAttribute("requiresInput").getBool();
        String description = getAttribute("description").getValue();
        AbstractCommand.Category category = getCategory();
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

    public static class AbstractCommandContribution extends CommandHierarchyNode<ArgumentContribution> {

        @SuppressWarnings("unused")
        public AbstractCommandContribution(Element element, NodeList subElements, Context context) {
            super(element, subElements, context);
        }

        @Override
        protected ArgumentContribution transformArgumentContribution(ArgumentContribution argumentContribution) {
            return argumentContribution;
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
