package net.robinfriedli.botify.command;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
    private List<Argument> arguments = Lists.newArrayList();
    private Map<String, String> setArguments = new HashMap<>();

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
        arguments.add(argument);
        return argument;
    }

    /**
     * @return true if the user used the provided argument when calling the command
     */
    public boolean argumentSet(String argument) {
        return setArguments.containsKey(argument);
    }

    /**
     * Add an argument used by the user to the setArguments when processing the command, verifying whether that argument
     * exists and meets all defined rules happens when {@link #complete()} is called.
     */
    public void setArgument(String argument) {
        setArgument(argument, "");
    }

    /**
     * Same as {@link #setArgument(String)} but assigns the given value provided by the user
     */
    public void setArgument(String argument, String value) {
        setArguments.put(argument, value);
    }

    /**
     * Completes the argument contribution by verifying the defined rules and assigning the provided values to the
     * arguments.
     */
    public void complete() {
        for (String setArgument : setArguments.keySet()) {
            if (arguments.stream().noneMatch(arg -> arg.getArgument().equalsIgnoreCase(setArgument))) {
                throw new InvalidCommandException(String.format("Unexpected argument '%s'", setArgument));
            }
        }

        List<Argument> selectedArguments = arguments
            .stream()
            .filter(arg -> setArguments.containsKey(arg.getArgument()))
            .collect(Collectors.toList());
        selectedArguments.forEach(arg -> arg.setValue(setArguments.get(arg.getArgument())));
        selectedArguments.forEach(arg -> arg.check(selectedArguments));
    }

    /**
     * @return all arguments that may be used with this command
     */
    public List<Argument> getArguments() {
        return arguments;
    }

    /**
     * @return the source command this ArgumentContribution was built for
     */
    public AbstractCommand getSourceCommand() {
        return sourceCommand;
    }

    /**
     * @return the given argument definition for the provided name
     */
    public Argument getArgument(String argument) {
        List<Argument> matches = arguments.stream().filter(arg -> arg.getArgument().equals(argument)).collect(Collectors.toList());

        if (matches.size() == 1) {
            return matches.get(0);
        } else if (matches.isEmpty()) {
            throw new IllegalArgumentException("Undefined argument " + argument);
        } else {
            throw new IllegalArgumentException("Duplicate argument definition for " + argument);
        }
    }

    /**
     * @return true if the ArgumentContribution for this command does not contain any arguments
     */
    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    /**
     * A single argument that may be described in this ArgumentContribution
     */
    public class Argument {

        private final String argument;
        private String value;
        private Set<String> excludedArguments;
        private Set<String> neededArguments;
        private String description;
        private boolean requiresValue;
        // useful for commands that only require input if a certain argument is set
        private boolean requiresInput;

        Argument(String argument) {
            this.argument = argument;
            this.excludedArguments = Sets.newHashSet();
            this.neededArguments = Sets.newHashSet();
            description = "";
        }

        /**
         * @return the string identifier for this argument description
         */
        public String getArgument() {
            return argument;
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
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("Invalid argument value. Expected a number but got '" + value + "'.");
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
         *
         * @param selectedArguments the list of all arguments that were used
         */
        void check(List<Argument> selectedArguments) {
            List<String> setArguments = selectedArguments.stream().map(Argument::getArgument).collect(Collectors.toList());
            for (String selectedArgument : setArguments) {
                if (excludedArguments.contains(selectedArgument)) {
                    String message = String.format("Conflicting arguments! %s may not be set when %s is set.", selectedArgument, argument);
                    throw new InvalidCommandException(message);
                }
            }

            for (String neededArgument : neededArguments) {
                if (!setArguments.contains(neededArgument)) {
                    String message = String.format("Invalid argument! %s may only be set if %s is set.", argument, neededArgument);
                    throw new InvalidCommandException(message);
                }
            }

            if (requiresValue && !hasValue()) {
                throw new InvalidCommandException("Argument " + argument + " requires an assigned value!");
            } else if (!requiresValue && hasValue()) {
                throw new InvalidCommandException("Argument " + argument + " does not require an assigned value!");
            }

            if (requiresInput && getSourceCommand().getCommandBody().isBlank()) {
                throw new InvalidCommandException("Argument " + argument + " requires additional command input.");
            }
        }
    }

}
