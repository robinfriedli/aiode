package net.robinfriedli.botify.command;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.stringlist.StringList;

public class ArgumentContribution {

    private List<Argument> arguments = Lists.newArrayList();

    public Argument map(String arg) {
        Argument argument = new Argument(arg);
        arguments.add(argument);
        return argument;
    }

    public void check(StringList setArguments) {
        for (String setArgument : setArguments) {
            if (arguments.stream().noneMatch(arg -> arg.getArgument().equalsIgnoreCase(setArgument))) {
                throw new InvalidCommandException(String.format("Unexpected argument '%s'", setArgument));
            }
        }

        List<Argument> selectedArguments = arguments
            .stream()
            .filter(arg -> setArguments.contains(arg.getArgument(), true))
            .collect(Collectors.toList());
        selectedArguments.forEach(arg -> arg.check(selectedArguments));
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    public static class Argument {

        private final String argument;
        private Set<String> excludedArguments;
        private Set<String> neededArguments;
        private String description;

        Argument(String argument) {
            this.argument = argument;
            this.excludedArguments = Sets.newHashSet();
            this.neededArguments = Sets.newHashSet();
            description = "";
        }

        public String getArgument() {
            return argument;
        }

        public Argument needsArguments(String... neededArguments) {
            Collections.addAll(this.neededArguments, neededArguments);
            return this;
        }

        public Argument excludesArguments(String... excludedArguments) {
            Collections.addAll(this.excludedArguments, excludedArguments);
            return this;
        }

        Set<String> getExcludedArguments() {
            return excludedArguments;
        }

        public Set<String> getNeededArguments() {
            return neededArguments;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
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
        }
    }

}
