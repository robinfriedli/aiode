package net.robinfriedli.botify.command.parser;

import net.robinfriedli.botify.command.AbstractCommand;

/**
 * Listener class tha listens to events fired when parsing a command using the {@link CommandParser}
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class CommandParseListener {

    /**
     * Event fired when handling a character returns a different mode (e.g. if the current character is an argument prefix)
     * than the mode that handled the character.
     *
     * @param previousMode the mode that handled the current character
     * @param newMode      the mode that was returned when handling the character, either a different mode or a different
     *                     instance of the same mode
     * @param index        the index of the character that caused the mode switch
     * @param character    the character that caused the mode switch
     */
    public void onModeSwitch(CommandParser.Mode previousMode, CommandParser.Mode newMode, int index, char character) {
    }

    /**
     * Fired when an argument has been fully parsed, including value. I.e. when {@link ArgumentBuildingMode#terminate()}
     * is called.
     *
     * @param argument the argument that was parsed
     * @param value    the value the argument has been assigned
     */
    public void onArgumentParsed(String argument, String value) {
    }

    /**
     * Fired when the command input has been fully parsed. I.e. when {@link CommandInputBuildingMode#terminate()}
     * has been called
     *
     * @param commandInput the parsed command input
     */
    public void onCommandInputParsed(String commandInput) {
    }

    /**
     * Fired when a command is done parsing
     *
     * @param command the command that has been parsed
     */
    public void onParseFinished(AbstractCommand command) {
    }

}
