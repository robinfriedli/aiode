package net.robinfriedli.botify.command;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import groovy.lang.GroovyShell;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.discord.property.properties.PrefixProperty;
import net.robinfriedli.botify.entities.xml.ArgumentContribution;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidArgumentException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.StringConverter;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.exceptions.ConversionException;

/**
 * Manages arguments in the context of one command execution. Each AbstractCommand instance receives it's own instance
 * of this class. This class manages what arguments were used with what assigned value (e.g. $select=5) when invoking
 * this command and provides general access to arguments available to this command across it's entire hierarchy.
 */
public class ArgumentController {

    private final AbstractCommand sourceCommand;
    private final CommandContribution commandContribution;
    private final Map<String, ArgumentUsage> usedArguments;
    private final GroovyShell groovyShell;

    public ArgumentController(AbstractCommand sourceCommand) {
        this.sourceCommand = sourceCommand;
        commandContribution = sourceCommand.getCommandContribution();
        usedArguments = new CaseInsensitiveMap<>();
        groovyShell = new GroovyShell();
        groovyShell.setVariable("command", sourceCommand);
    }

    /**
     * @return the arguments used when calling this command mapped to the {@link ArgumentUsage}
     */
    public Map<String, ArgumentUsage> getUsedArguments() {
        return usedArguments;
    }

    /**
     * Get a defined argument definition for this command or any of its super classes or null.
     *
     * @param arg the identifier of the argument, case insensitive
     * @return the found argument definition
     */
    public ArgumentContribution get(String arg) {
        return commandContribution.getArgument(arg);
    }

    /**
     * Get an argument that was used when calling this command by its identifier
     *
     * @param arg the argument identifier
     * @return the {@link ArgumentUsage} instance.
     */
    public ArgumentUsage getUsedArgument(String arg) {
        return usedArguments.get(arg);
    }

    /**
     * same as {@link #get(String)} but throws an exception if no argument definition was found
     */
    public ArgumentContribution require(String arg) {
        return require(arg, InvalidArgumentException::new);
    }

    public ArgumentContribution require(String arg, Function<String, ? extends RuntimeException> exceptionProducer) {
        ArgumentContribution argument = get(arg);

        if (argument == null) {
            throw exceptionProducer.apply(String.format("Undefined argument '%s' on command '%s'.", arg, sourceCommand.getIdentifier()));
        }

        return argument;
    }

    /**
     * @return true if the user used the provided argument when calling the command
     */
    public boolean argumentSet(String argument) {
        return usedArguments.containsKey(argument);
    }

    /**
     * Add an argument used by the user to the setArguments when processing the command, verifying whether that argument
     * exists and meets all defined rules happens when {@link #verify()} is called.
     */
    public void setArgument(String argument) {
        setArgument(argument, "");
    }

    /**
     * Same as {@link #setArgument(String)} but assigns the given value provided by the user
     */
    public void setArgument(String argument, String value) {
        ArgumentContribution arg = require(argument);
        usedArguments.put(argument, new ArgumentUsage(arg, value));
    }

    public boolean hasArguments() {
        return !getArguments().isEmpty();
    }

    /**
     * Verifies all set argument rules
     */
    public void verify() {
        for (ArgumentUsage value : usedArguments.values()) {
            value.verify();
        }
    }

    /**
     * @return all arguments that may be used with this command
     */
    public Set<ArgumentContribution> getArguments() {
        return commandContribution.getArguments();
    }

    /**
     * @return the source command this ArgumentContribution was built for
     */
    public AbstractCommand getSourceCommand() {
        return sourceCommand;
    }

    /**
     * Copy state from an existing ArgumentContribution instance onto this one. Useful when forking commands. The
     * ArgumentContribution must be from the same Command type.
     *
     * @param argumentController the old argument controller
     */
    public void transferValues(ArgumentController argumentController) {
        Class<? extends AbstractCommand> currentCommandType = sourceCommand.getClass();
        Class<? extends AbstractCommand> providedCommandType = argumentController.getSourceCommand().getClass();
        if (!currentCommandType.equals(providedCommandType)) {
            throw new IllegalArgumentException(
                String.format("Provided argumentContribution is of a different Command type. Current type: %s; Provided type:%s",
                    currentCommandType, providedCommandType)
            );
        }

        for (Map.Entry<String, ArgumentUsage> usageEntry : argumentController.getUsedArguments().entrySet()) {
            usedArguments.put(usageEntry.getKey(), usageEntry.getValue());
        }
    }

    /**
     * Describes a single argument used in this command invocation, referencing the persistent argument description and
     * the value assigned when invoking the command, e.g. $select=5
     */
    public class ArgumentUsage {

        private final ArgumentContribution argument;
        private final String value;

        public ArgumentUsage(ArgumentContribution argument, String value) {
            this.argument = argument;
            this.value = value;
        }

        public ArgumentContribution getArgument() {
            return argument;
        }

        public String getValue() {
            return value;
        }

        /**
         * @return the value assigned to this argument cast to the given type
         */
        public <E> E getValue(Class<E> type) {
            try {
                return StringConverter.convert(value, type);
            } catch (ConversionException e) {
                throw new InvalidCommandException(String.format("Invalid argument value. Cannot convert '%s' to type %s",
                    value, type.getSimpleName()));
            }
        }

        /**
         * @return true if this argument has been assigned a value. This is false for most arguments as most arguments
         * follow a set or not set principle without requiring a value.
         */
        public boolean hasValue() {
            return !value.isEmpty();
        }

        public void verify() {
            for (XmlElement excludedArgument : argument.getExcludedArguments()) {
                String excludedArgumentIdentifier = excludedArgument.getAttribute("argument").getValue();
                if (argumentSet(excludedArgumentIdentifier)) {
                    if (excludedArgument.hasAttribute("message")) {
                        throw new InvalidCommandException(excludedArgument.getAttribute("message").getValue());
                    } else {
                        throw new InvalidCommandException(String.format("Argument '%s' can not be set if '%s' is set.",
                            this.argument.getIdentifier(), excludedArgumentIdentifier));
                    }
                }
            }

            for (XmlElement requiredArgument : argument.getRequiredArguments()) {
                String requiredArgumentIdentifier = requiredArgument.getAttribute("argument").getValue();
                if (!argumentSet(requiredArgumentIdentifier)) {
                    if (requiredArgument.hasAttribute("message")) {
                        throw new InvalidCommandException(requiredArgument.getAttribute("message").getValue());
                    } else {
                        throw new InvalidCommandException(String.format("Argument '%s' may only be set if argument '%s' is set.",
                            this.argument.getIdentifier(), requiredArgumentIdentifier));
                    }
                }
            }

            if (argument.getAttribute("requiresValue").getBool() && !hasValue()) {
                throw new InvalidCommandException("Argument " + argument.getIdentifier() + " requires an assigned value!");
            }

            if (argument.getAttribute("requiresInput").getBool() && getSourceCommand().getCommandInput().isBlank()) {
                throw new InvalidCommandException("Argument " + argument.getIdentifier() + " requires additional command input.");
            }

            groovyShell.setVariable("value", hasValue() ? getValue(argument.getValueType()) : value);

            for (XmlElement rule : argument.getRules()) {
                String condition = rule.getTextContent();
                if (!evaluateScript(condition)) {
                    String prefix = PrefixProperty.getEffectiveCommandStartForCurrentContext();
                    char argumentPrefix = ArgumentPrefixProperty.getForCurrentContext().getArgumentPrefix();
                    throw new InvalidCommandException(String.format(rule.getAttribute("errorMessage").getValue(), prefix, argumentPrefix, value));
                }
            }

            if (hasValue()) {
                for (XmlElement valueCheck : argument.getValueChecks()) {
                    String check = valueCheck.getAttribute("check").getValue();
                    if (!evaluateScript(check)) {
                        String prefix = PrefixProperty.getEffectiveCommandStartForCurrentContext();
                        char argumentPrefix = ArgumentPrefixProperty.getForCurrentContext().getArgumentPrefix();
                        throw new InvalidCommandException(String.format(valueCheck.getAttribute("errorMessage").getValue(), prefix, argumentPrefix));
                    }
                }
            }
        }

        private boolean evaluateScript(String script) {
            try {
                return (boolean) groovyShell.evaluate(script);
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Groovy script does not return boolean", e);
            }
        }

    }

}
