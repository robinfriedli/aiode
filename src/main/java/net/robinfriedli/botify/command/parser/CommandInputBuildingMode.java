package net.robinfriedli.botify.command.parser;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;

/**
 * Records the command input. E.g. if the used enters 'add $list 00s Rock Anthems $to rock' this mode will set
 * '00s Rock Anthems' as command input.
 */
public class CommandInputBuildingMode implements CommandParser.Mode {

    private final AbstractCommand command;
    private final CommandParser commandParser;
    private final char argumentPrefix;
    private final StringBuilder commandInputBuilder;
    private char lastChar;

    public CommandInputBuildingMode(AbstractCommand command, CommandParser commandParser, char argumentPrefix) {
        this.command = command;
        this.commandParser = commandParser;
        this.argumentPrefix = argumentPrefix;
        commandInputBuilder = new StringBuilder();
    }

    @Override
    public CommandParser.Mode handle(char character) {
        if ((Character.isWhitespace(lastChar) || lastChar == '"')
            && (character == argumentPrefix || character == ArgumentPrefixProperty.DEFAULT)
        ) {
            char nextChar = commandParser.peekNextChar();
            if (!(nextChar == 0 || Character.isWhitespace(nextChar))) {
                terminate();
                return new ArgumentBuildingMode(command, commandParser, argumentPrefix, true);
            }
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
        String existingInput = command.getCommandInput();

        String finalInput;
        if (existingInput != null && !existingInput.isEmpty()) {
            finalInput = existingInput + commandInput;
        } else {
            finalInput = commandInput;
        }

        command.setCommandInput(finalInput);
        commandParser.fireOnCommandInputParsed(commandInput);
    }
}
