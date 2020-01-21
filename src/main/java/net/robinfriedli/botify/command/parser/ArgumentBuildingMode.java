package net.robinfriedli.botify.command.parser;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentController;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.exceptions.CommandParseException;
import net.robinfriedli.botify.exceptions.UserException;

/**
 * Mode implementation that is used to build one argument per instance. This mode is typically switched to if the last
 * character was an argument prefix. Records the entered argument and its assigned value, if available. Most arguments,
 * such as '$list' or '$spotify' follow a-set-or-not-set principle and do not require a value and typically get terminated
 * by whitespace or a following argument prefix. Arguments that are within the command head, meaning before the command
 * input may be assigned a value using the '=' character (e.g. 'play $youtube $limit=4 foo' or 'play foo="ba ar" input').
 * Inline arguments that are within the command input receive the command input that follows them up the next argument
 * as value (e.g. when entering 'move track $on list $to index' the argument 'on' receives 'list' as value and the argument
 * 'to' receives 'index' as value).
 */
public class ArgumentBuildingMode implements CommandParser.Mode {

    private final AbstractCommand command;
    private final CommandParser commandParser;
    private final char argumentPrefix;
    private final boolean isInline;
    private final int conceptionIndex;

    private StringBuilder argumentBuilder;
    private StringBuilder argumentValueBuilder;

    private boolean isRecodingValue;

    public ArgumentBuildingMode(AbstractCommand command, CommandParser commandParser, char argumentPrefix) {
        this(command, commandParser, argumentPrefix, false);
    }

    public ArgumentBuildingMode(AbstractCommand command, CommandParser commandParser, char argumentPrefix, boolean isInline) {
        this.command = command;
        this.commandParser = commandParser;
        this.argumentPrefix = argumentPrefix;
        this.isInline = isInline;
        conceptionIndex = commandParser.getCurrentPosition();
        argumentBuilder = new StringBuilder();
        argumentValueBuilder = new StringBuilder();
    }

    @Override
    public CommandParser.Mode handle(char character) {
        if (character == '=') {
            isRecodingValue = true;
            return this;
        } else if (Character.isWhitespace(character)) {
            if (isInline) {
                if (isRecodingValue) {
                    argumentValueBuilder.append(character);
                } else {
                    isRecodingValue = true;
                }
                return this;
            } else {
                terminate();
                return new ScanningMode(command, commandParser, argumentPrefix);
            }
        } else if (character == argumentPrefix || character == ArgumentPrefixProperty.DEFAULT) {
            terminate();
            return new ArgumentBuildingMode(command, commandParser, argumentPrefix, isInline);
        } else {
            if (isRecodingValue) {
                argumentValueBuilder.append(character);
            } else {
                argumentBuilder.append(character);
            }
            return this;
        }
    }

    @Override
    public CommandParser.Mode handleLiteral(char character) {
        if (isRecodingValue) {
            argumentValueBuilder.append(character);
        } else {
            argumentBuilder.append(character);
        }
        return this;
    }

    @Override
    public void terminate() {
        try {
            ArgumentController argumentController = command.getArgumentController();
            String argument = argumentBuilder.toString().trim();
            String argumentValue = argumentValueBuilder.toString().trim();
            argumentController.setArgument(argument, argumentValue);
            commandParser.fireOnArgumentParsed(argument, argumentValue);
        } catch (UserException e) {
            throw new CommandParseException(e.getMessage(), command.getCommandBody(), e, conceptionIndex);
        }
    }
}
