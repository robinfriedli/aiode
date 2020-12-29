package net.robinfriedli.botify.entities.xml;

import java.util.Map;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.argument.ArgumentContributionDelegate;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

import static net.robinfriedli.jxp.queries.Conditions.*;

public abstract class CommandHierarchyNode<T extends ArgumentContributionDelegate> extends GenericClassContribution<AbstractCommand> {

    private final Object setupLock = new Object();

    private Map<String, T> argumentContributions;

    public CommandHierarchyNode(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    public Map<String, T> getArguments() {
        if (argumentContributions == null) {
            synchronized (setupLock) {
                // recheck after acquiring lock
                if (argumentContributions != null) {
                    return argumentContributions;
                }

                Map<String, T> argumentContributions = new CaseInsensitiveMap<>();
                collectArgumentsForSuperClass(getImplementationClass().getSuperclass(), argumentContributions, getContext());
                addArgumentsFromNode(this, argumentContributions);
                this.argumentContributions = argumentContributions;
                return argumentContributions;
            }
        }

        return argumentContributions;
    }

    /**
     * Get a specific argument from the hierarchic command definition structure or null, case insensitive.
     *
     * @param argument argument identifier
     * @return the argument defined on this or a CommandHierarchyNode defined for a super class or null
     */
    public T getArgument(String argument) {
        return argumentContributions.get(argument);
    }

    protected abstract T transformArgumentContribution(ArgumentContribution argumentContribution);

    private void collectArgumentsForSuperClass(Class<?> superclass, Map<String, T> map, Context context) {
        if (superclass == null) {
            return;
        }

        collectArgumentsForSuperClass(superclass.getSuperclass(), map, context);

        CommandHierarchyNode<?> commandHierarchyNode = context.query(and(
            instanceOf(CommandHierarchyNode.class),
            xmlElement -> {
                String classAttribute = ((CommandHierarchyNode<?>) xmlElement).defineClassAttribute();
                return attribute(classAttribute).is(superclass.getName()).test(xmlElement);
            }
        ), CommandHierarchyNode.class).getOnlyResult();

        if (commandHierarchyNode != null) {
            addArgumentsFromNode(commandHierarchyNode, map);
        }
    }

    private void addArgumentsFromNode(CommandHierarchyNode<?> node, Map<String, T> map) {
        for (ArgumentContribution argumentContribution : node.getInstancesOf(ArgumentContribution.class)) {
            map.put(argumentContribution.getIdentifier(), transformArgumentContribution(argumentContribution));
        }
        node.query(tagName("removeArgument"))
            .getResultStream()
            .forEach(removeArgument -> {
                String removeArgumentIdentifier = removeArgument.getAttribute("identifier").getValue();
                T removed = map.remove(removeArgumentIdentifier);
                if (removed == null) {
                    throw new IllegalStateException(
                        String.format(
                            "Could not remove argument '%s'. Either no such argument exists or it has already been removed further up in the hierarchy.",
                            removeArgumentIdentifier
                        )
                    );
                }
            });
    }

}
