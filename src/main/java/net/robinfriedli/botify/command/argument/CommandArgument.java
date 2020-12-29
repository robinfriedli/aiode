package net.robinfriedli.botify.command.argument;

import java.util.List;
import java.util.Set;

import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.entities.xml.ArgumentContribution;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.jxp.api.XmlElement;
import org.jetbrains.annotations.Nullable;

/**
 * Configured argument of a command. An instance of this class is created for each command for inherited arguments.
 */
public class CommandArgument implements ArgumentDefinition, ArgumentContributionDelegate, PermissionTarget {

    private final CommandContribution commandContribution;
    private final ArgumentContribution argumentContribution;

    public CommandArgument(CommandContribution commandContribution, ArgumentContribution argumentContribution) {
        this.commandContribution = commandContribution;
        this.argumentContribution = argumentContribution;
    }

    public CommandContribution getCommandContribution() {
        return commandContribution;
    }

    public ArgumentContribution getArgumentContribution() {
        return argumentContribution;
    }

    @Override
    public String getPermissionTargetIdentifier() {
        return argumentContribution.getIdentifier();
    }

    @Override
    public TargetType getPermissionTargetType() {
        return TargetType.ARGUMENT;
    }

    @Nullable
    @Override
    public PermissionTarget getChildTarget(String identifier) {
        return null;
    }

    @Nullable
    @Override
    public Set<? extends PermissionTarget> getChildren() {
        return null;
    }

    @Nullable
    @Override
    public PermissionTarget getParentTarget() {
        return commandContribution;
    }

    @Override
    public ArgumentContribution unwrapArgumentContribution() {
        return getArgumentContribution();
    }

    @Override
    public String getIdentifier() {
        return argumentContribution.getIdentifier();
    }

    @Override
    public String getDescription() {
        return argumentContribution.getDescription();
    }

    @Override
    public List<XmlElement> getExcludedArguments() {
        return argumentContribution.getExcludedArguments();
    }

    @Override
    public List<XmlElement> getRequiredArguments() {
        return argumentContribution.getRequiredArguments();
    }

    @Override
    public List<XmlElement> getRules() {
        return argumentContribution.getRules();
    }

    @Override
    public List<XmlElement> getValueChecks() {
        return argumentContribution.getValueChecks();
    }

    @Override
    public Class<?> getValueType() {
        return argumentContribution.getValueType();
    }

    @Override
    public boolean requiresValue() {
        return argumentContribution.requiresValue();
    }

    @Override
    public boolean requiresInput() {
        return argumentContribution.requiresInput();
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Nullable
    @Override
    public PermissionTarget getPermissionTarget() {
        return this;
    }
}
