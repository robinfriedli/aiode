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
    private final char argumentPrefix;

    public ScanningMode(AbstractCommand command, CommandParser commandParser, char argumentPrefix) {
        this.command = command;
        this.commandParser = commandParser;
        this.argumentPrefix = argumentPrefix;
    }

    @Override
    public CommandParser.Mode handle(char character) {
        if (' ' == character) {
            return this;
        } else {
            if (argumentPrefix == character || ArgumentPrefixProperty.DEFAULT == character) {
                return new ArgumentBuildingMode(command, commandParser, argumentPrefix);
            } else {
                return new CommandInputBuildingMode(command, commandParser, argumentPrefix).handle(character);
            }
        }
    }

    @Override
    public CommandParser.Mode handleLiteral(char character) {
        return new CommandInputBuildingMode(command, commandParser, argumentPrefix).handleLiteral(character);
    }

    @Override
    public void terminate() {
        // nothing to do
    }
}
