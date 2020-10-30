package net.robinfriedli.botify.command.parser;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;

/**
 * Starting mode when parsing a command that scans for argument prefixes and switches into a {@link ArgumentBuildingMode}
 * or {@link CommandInputBuildingMode}
 */
public class ScanningMode implements CommandParser.Mode {

    private final AbstractCommand command;
    private final CommandParser commandParser;
    private final ArgumentPrefixProperty.Config argumentPrefixConfig;

    public ScanningMode(AbstractCommand command, CommandParser commandParser, ArgumentPrefixProperty.Config argumentPrefixConfig) {
        this.command = command;
        this.commandParser = commandParser;
        this.argumentPrefixConfig = argumentPrefixConfig;
    }

    @Override
    public CommandParser.Mode handle(char character) {
        if (Character.isWhitespace(character)) {
            return this;
        } else {
            if (argumentPrefixConfig.getArgumentPrefix() == character || argumentPrefixConfig.getDefaultArgumentPrefix() == character) {
                char nextChar = commandParser.peekNextChar();
                if (nextChar == 0 || Character.isWhitespace(nextChar)) {
                    return new CommandInputBuildingMode(command, commandParser, argumentPrefixConfig).handleLiteral(character);
                }
                return new ArgumentBuildingMode(command, commandParser, argumentPrefixConfig);
            } else {
                return new CommandInputBuildingMode(command, commandParser, argumentPrefixConfig).handle(character);
            }
        }
    }

    @Override
    public CommandParser.Mode handleLiteral(char character) {
        return new CommandInputBuildingMode(command, commandParser, argumentPrefixConfig).handleLiteral(character);
    }

    @Override
    public void terminate() {
        // nothing to do
    }
}
