package net.robinfriedli.botify.command;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import com.google.api.client.util.Lists;
import com.google.common.collect.Sets;
import net.robinfriedli.botify.exceptions.InvalidArgumentException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.StringConverter;
import net.robinfriedli.jxp.exceptions.ConversionException;

/**
 * Defines and handles arguments that may be used with a command. An ArgumentContribution is set up when instantiating
 * an {@link AbstractCommand} by calling the {@link AbstractCommand#setupArguments()} method that may be overridden
 * in command implementations.
 */
public class ArgumentContribution {

    private final AbstractCommand sourceCommand;
    private final Map<String, Argument> definedArguments = new CaseInsensitiveMap<>();

    public ArgumentContribution(AbstractCommand sourceCommand) {
        this.sourceCommand = sourceCommand;
    }

    /**
     * Build an argument that may be used with this command.
     * E.g.:
     * <p>
     * argumentContribution.map("album").needsArguments("spotify").excludesArguments("list").setRequiresInput(true)
     * .setDescription("Search for a Spotify album. Note that this argument is only required when searching, not when entering a URL.");
     * </p>
     */
    public Argument map(String arg) {
        Argument argument = new Argument(arg);
        definedArguments.put(arg, argument);
        return argument;
    }

    /**
     * Remove an argument definition from this ArgumentContribution. Useful for subclasses of commands that have similar
     * arguments but might exclude a few.
     *
     * @param arg the argument identifier
     */
    public void remove(String arg) {
        definedArguments.remove(arg);
    }

    /**
     * @param arg the identifier of the argument, case insensitive
     * @return the found argument definition
     */
    public Argument get(String arg) {
        return definedArguments.get(arg);
    }

    /**
     * same as {@link #get(String)} but throws an exception if no argument definition was found
     */
    public Argument require(String arg) {
        return require(arg, InvalidArgumentException.class);
    }

    public Argument require(String arg, Class<? extends RuntimeException> toThrow) {
        Argument argument = get(arg);

        if (argument == null) {
            try {
                throw toThrow.getConstructor(String.class)
                    .newInstance(String.format("Undefined argument '%s' on command '%s'.", arg, sourceCommand.getIdentifier()));
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Could not instantiate " + toThrow);
            }
        }

        return argument;
    }

    /**
     * @return true if the user used the provided argument when calling the command
     */
    public boolean argumentSet(String argument) {
        Argument arg = require(argument, IllegalArgumentException.class);
        return arg.isSet();
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
        Argument arg = require(argument);
        arg.setSet(true);
        arg.setValue(value);
    }

    /**
     * Verifies all set argument rules
     */
    public void verify() {
        for (Argument value : definedArguments.values()) {
            value.check();
        }
    }

    /**
     * @return all arguments that may be used with this command
     */
    public List<Argument> getArguments() {
        return Lists.newArrayList(definedArguments.values());
    }

    /**
     * @return the source command this ArgumentContribution was built for
     */
    public AbstractCommand getSourceCommand() {
        return sourceCommand;
    }

    /**
     * @return true if the ArgumentContribution for this command does not contain any arguments
     */
    public boolean isEmpty() {
        return definedArguments.isEmpty();
    }

    /**
     * A single argument that may be described in this ArgumentContribution
     */
    public class Argument {

        private final String identifier;
        private String value;
        private Set<String> excludedArguments;
        private Set<String> neededArguments;
        private String description;
        private boolean set;
        private boolean requiresValue;
        // useful for commands that only require input if a certain argument is set
        private boolean requiresInput;

        Argument(String identifier) {
            this.identifier = identifier;
            this.excludedArguments = Sets.newHashSet();
            this.neededArguments = Sets.newHashSet();
            description = "";
        }

        /**
         * @return the string identifier for this argument description
         */
        public String getIdentifier() {
            return identifier;
        }

        /**
         * Assign a value to this argument as entered by the user
         */
        public void setValue(String value) {
            this.value = value;
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

        /**
         * defines a rule where this argument may only be used if the provided arguments were used as well
         */
        public Argument needsArguments(String... neededArguments) {
            Collections.addAll(this.neededArguments, neededArguments);
            return this;
        }

        /**
         * defines a rule where this argument can not be used if any of the provided arguments are set
         */
        public Argument excludesArguments(String... excludedArguments) {
            Collections.addAll(this.excludedArguments, excludedArguments);
            return this;
        }


        /**
         * define a rule where this argument requires an assigned value
         */
        public Argument setRequiresValue(boolean requiresValue) {
            this.requiresValue = requiresValue;
            return this;
        }

        /**
         * define a rule where if this argument is used the command requires additional input. E.g. the play command may
         * be used without any additional input to, for instance, resume playback but when the $spotify argument is set
         * a track name must be provided as command input
         */
        public Argument setRequiresInput(boolean requiresInput) {
            this.requiresInput = requiresInput;
            return this;
        }

        Set<String> getExcludedArguments() {
            return excludedArguments;
        }

        public Set<String> getNeededArguments() {
            return neededArguments;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * Verify that this argument matches all configured rules
         */
        void check() {
            if (isSet()) {
                for (String neededArgument : getNeededArguments()) {
                    Argument argument = require(neededArgument);
                    if (!argument.isSet()) {
                        throw new InvalidCommandException(String.format("Argument '%s' may only be set if argument '%s' is set.",
                            getIdentifier(), argument.getIdentifier()));
                    }
                }

                for (String excludedArgument : getExcludedArguments()) {
                    Argument argument = require(excludedArgument);
                    if (argument.isSet()) {
                        throw new InvalidCommandException(String.format("Argument '%s' can not be set if '%s' is set.",
                            getIdentifier(), argument.getIdentifier()));
                    }
                }

                if (requiresValue && !hasValue()) {
                    throw new InvalidCommandException("Argument " + identifier + " requires an assigned value!");
                }

                if (requiresInput && getSourceCommand().getCommandInput().isBlank()) {
                    throw new InvalidCommandException("Argument " + identifier + " requires additional command input.");
                }
            }
        }

        public boolean isSet() {
            return set;
        }

        public void setSet(boolean set) {
            this.set = set;
        }
    }

}
