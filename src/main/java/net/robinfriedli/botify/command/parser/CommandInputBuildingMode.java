package net.robinfriedli.botify.command.parser;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.discord.properties.ArgumentPrefixProperty;

/**
 * Records the command input. E.g. if the used enters 'add $list 00s Rock Anthems $to rock' this mode will set
 * '00s Rock Anthems' as command input.
 */
public class CommandInputBuildingMode implements CommandParser.Mode {

    private final AbstractCommand command;
    private final CommandParser commandParser;
    private final char argumentPrefix;
    private StringBuilder commandInputBuilder;
    private char lastChar;

    public CommandInputBuildingMode(AbstractCommand command, CommandParser commandParser, char argumentPrefix) {
        this.command = command;
        this.commandParser = commandParser;
        this.argumentPrefix = argumentPrefix;
        commandInputBuilder = new StringBuilder();
    }

    @Override
    public CommandParser.Mode handle(char character) {
        if ((lastChar == ' ' || lastChar == '"') && (character == argumentPrefix || character == ArgumentPrefixProperty.DEFAULT)) {
            terminate();
            return new ArgumentBuildingMode(command, commandParser, argumentPrefix, true);
        }
        commandInputBuilder.append(character);
        lastChar = character;
        return this;
    }

    @Override
    public CommandParser.Mode handleLiteral(char character) {
        commandInputBuilder.append(character);
        return this;
    }

    @Override
    public void terminate() {
        String commandInput = commandInputBuilder.toString().trim();
        command.setCommandInput(commandInput);
        commandParser.fireOnCommandInputParsed(commandInput);
    }
}
