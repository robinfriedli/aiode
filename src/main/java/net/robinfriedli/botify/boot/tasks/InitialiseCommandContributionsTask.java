package net.robinfriedli.botify.boot.tasks;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.JDA;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.ArgumentContribution;
import net.robinfriedli.botify.entities.xml.CommandHierarchyNode;
import net.robinfriedli.botify.entities.xml.StartupTaskContribution;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.jetbrains.annotations.Nullable;

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
        List<CommandHierarchyNode> commandHierarchyNodes = commandContributionContext.getInstancesOf(CommandHierarchyNode.class);

        for (CommandHierarchyNode commandHierarchyNode : commandHierarchyNodes) {
            Set<ArgumentContribution> argumentContributions = commandHierarchyNode.getArguments();

            for (ArgumentContribution argumentContribution : argumentContributions) {
                Set<XmlElement> excludedArguments = argumentContribution.getExcludedArguments();
                Set<XmlElement> requiredArguments = argumentContribution.getRequiredArguments();

                validateReferencedArguments(commandHierarchyNode, argumentContribution, excludedArguments);
                validateReferencedArguments(commandHierarchyNode, argumentContribution, requiredArguments);
            }
        }
    }

    private void validateReferencedArguments(CommandHierarchyNode commandHierarchyNode, ArgumentContribution argumentContribution, Collection<XmlElement> arguments) {
        for (XmlElement argument : arguments) {
            String argumentIdentifier = argument.getAttribute("argument").getValue();
            ArgumentContribution referencedArgument = commandHierarchyNode.getArgument(argumentIdentifier);

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
