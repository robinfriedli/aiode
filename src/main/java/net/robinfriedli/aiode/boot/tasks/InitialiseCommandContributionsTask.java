package net.robinfriedli.aiode.boot.tasks;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.JDA;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.argument.ArgumentContributionDelegate;
import net.robinfriedli.aiode.entities.xml.ArgumentContribution;
import net.robinfriedli.aiode.entities.xml.CommandHierarchyNode;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unchecked")
public class InitialiseCommandContributionsTask implements StartupTask {

    private final CommandManager commandManager;
    private final StartupTaskContribution contribution;

    public InitialiseCommandContributionsTask(CommandManager commandManager, StartupTaskContribution contribution) {
        this.commandManager = commandManager;
        this.contribution = contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) throws Exception {
        Context commandContributionContext = commandManager.getCommandContributionContext();
        @SuppressWarnings("rawtypes")
        List<CommandHierarchyNode> commandHierarchyNodes = commandContributionContext.getInstancesOf(CommandHierarchyNode.class);

        for (@SuppressWarnings("unchecked") CommandHierarchyNode<ArgumentContributionDelegate> commandHierarchyNode : commandHierarchyNodes) {
            Map<String, ArgumentContributionDelegate> argumentContributions = commandHierarchyNode.getArguments();

            for (ArgumentContributionDelegate argumentContributionDelegate : argumentContributions.values()) {
                ArgumentContribution argumentContribution = argumentContributionDelegate.unwrapArgumentContribution();
                List<XmlElement> excludedArguments = argumentContribution.getExcludedArguments();
                List<XmlElement> requiredArguments = argumentContribution.getRequiredArguments();

                validateReferencedArguments(commandHierarchyNode, argumentContribution, excludedArguments);
                validateReferencedArguments(commandHierarchyNode, argumentContribution, requiredArguments);
            }
        }
    }

    private void validateReferencedArguments(CommandHierarchyNode<?> commandHierarchyNode, ArgumentContribution argumentContribution, Collection<XmlElement> arguments) {
        for (XmlElement argument : arguments) {
            String argumentIdentifier = argument.getAttribute("argument").getValue();
            ArgumentContribution referencedArgument = commandHierarchyNode.getArgument(argumentIdentifier).unwrapArgumentContribution();

            if (referencedArgument == null) {
                throw new IllegalStateException(
                    String.format(
                        "Invalid command configuration for node '%s'. Cannot find referenced argument identifier '%s' required for element %s on argument %s.",
                        commandHierarchyNode.getId(),
                        argumentIdentifier,
                        argument,
                        argumentContribution
                    )
                );
            }
        }
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }
}
