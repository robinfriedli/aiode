package net.robinfriedli.botify.entities.xml;

import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Query;
import org.w3c.dom.Element;

import static net.robinfriedli.jxp.queries.Conditions.*;

public abstract class CommandHierarchyNode extends GenericClassContribution<AbstractCommand> {

    private Set<ArgumentContribution> argumentContributions;

    public CommandHierarchyNode(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    public Set<ArgumentContribution> getArguments() {
        if (argumentContributions == null) {
            Set<ArgumentContribution> argumentContributions = Sets.newHashSet();
            collectArgumentsForSuperClass(getImplementationClass().getSuperclass(), argumentContributions, getContext());
            addArgumentsFromNode(this, argumentContributions);
            this.argumentContributions = argumentContributions;
            return argumentContributions;
        }

        return argumentContributions;
    }

    /**
     * Get a specific argument from the hierarchic command definition structure or null, case insensitive.
     *
     * @param argument argument identifier
     * @return the argument defined on this or a CommandHierarchyNode defined for a super class or null
     */
    public ArgumentContribution getArgument(String argument) {
        return Query.evaluate(attribute("identifier").fuzzyIs(argument)).execute(Lists.newArrayList(getArguments()), ArgumentContribution.class).getOnlyResult();
    }

    private void collectArgumentsForSuperClass(Class<? super AbstractCommand> superclass, Set<ArgumentContribution> set, Context context) {
        if (superclass == null) {
            return;
        }

        collectArgumentsForSuperClass(superclass.getSuperclass(), set, context);

        CommandHierarchyNode commandHierarchyNode = context.query(and(
            instanceOf(CommandHierarchyNode.class),
            xmlElement -> {
                String classAttribute = ((CommandHierarchyNode) xmlElement).defineClassAttribute();
                return attribute(classAttribute).is(superclass.getName()).test(xmlElement);
            }
        ), CommandHierarchyNode.class).getOnlyResult();

        if (commandHierarchyNode != null) {
            addArgumentsFromNode(commandHierarchyNode, set);
        }
    }

    private void addArgumentsFromNode(CommandHierarchyNode node, Set<ArgumentContribution> set) {
        set.addAll(node.getInstancesOf(ArgumentContribution.class));
        node.query(tagName("removeArgument"))
            .getResultStream()
            .forEach(removeArgument -> {
                String removeArgumentIdentifier = removeArgument.getAttribute("identifier").getValue();
                boolean removed = set.removeIf(argument -> removeArgumentIdentifier.equals(argument.getIdentifier()));
                if (!removed) {
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
