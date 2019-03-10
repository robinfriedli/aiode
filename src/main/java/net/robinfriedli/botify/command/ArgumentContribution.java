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

public class ArgumentContribution {

    private final AbstractCommand sourceCommand;
    private List<Argument> arguments = Lists.newArrayList();
    private Map<String, String> setArguments = new HashMap<>();

    public ArgumentContribution(AbstractCommand sourceCommand) {
        this.sourceCommand = sourceCommand;
    }

    public Argument map(String arg) {
        Argument argument = new Argument(arg);
        arguments.add(argument);
        return argument;
    }

    public boolean argumentSet(String argument) {
        return setArguments.containsKey(argument);
    }

    public void setArgument(String argument) {
        setArgument(argument, "");
    }

    public void setArgument(String argument, String value) {
        setArguments.put(argument, value);
    }

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

    public List<Argument> getArguments() {
        return arguments;
    }

    public AbstractCommand getSourceCommand() {
        return sourceCommand;
    }

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

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

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

        public String getArgument() {
            return argument;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public <E> E getValue(Class<E> type) {
            try {
                return StringConverter.convert(value, type);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("Invalid argument value. Expected a number but got '" + value + "'.");
            }
        }

        public boolean hasValue() {
            return !value.isEmpty();
        }

        public Argument needsArguments(String... neededArguments) {
            Collections.addAll(this.neededArguments, neededArguments);
            return this;
        }

        public Argument excludesArguments(String... excludedArguments) {
            Collections.addAll(this.excludedArguments, excludedArguments);
            return this;
        }

        public Argument setRequiresValue(boolean requiresValue) {
            this.requiresValue = requiresValue;
            return this;
        }

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
