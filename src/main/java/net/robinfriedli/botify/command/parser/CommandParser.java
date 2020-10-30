package net.robinfriedli.botify.command.parser;

import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.interceptor.interceptors.CommandParserInterceptor;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.exceptions.CommandParseException;
import net.robinfriedli.botify.exceptions.IllegalEscapeCharacterException;
import net.robinfriedli.botify.exceptions.UnclosedQuotationsException;
import net.robinfriedli.botify.exceptions.UserException;

/**
 * Parses the body of a command and builds the used arguments and the command input. The CommandParser handles each
 * entered character individually with a certain {@link Mode}, starting with the {@link ScanningMode}. Each character
 * returns the {@link Mode} with which to handle the next character.
 * <p>
 * E.g. if the user enters '$botify add $list $youtube $limit=5 linkin park $to favs', the {@link CommandParserInterceptor}
 * will call {@link #parse()} with '$list $youtube $limit=5 linkin park $to favs'.
 * The CommandParser will then recognise all used argument (list, youtube, limit and to) and assign the argument values
 * (limit = 5 and to = favs) and set the command input to 'linkin park'
 */
public class CommandParser {

    public static final Set<Character> META = ImmutableSet.of(ArgumentPrefixProperty.DEFAULT_FALLBACK, '"', '\\', '=', ' ');

    private final AbstractCommand command;
    private final ArgumentPrefixProperty.Config argumentPrefixConfig;
    private final CommandParseListener[] listeners;
    private final Logger logger;

    private char[] chars;

    private int currentPosition = 0;
    private Mode currentMode;
    private boolean isEscaped;
    private boolean isOpenQuotation;

    public CommandParser(AbstractCommand command, ArgumentPrefixProperty.Config argumentPrefixConfig, CommandParseListener... listeners) {
        this.command = command;
        this.argumentPrefixConfig = argumentPrefixConfig;
        this.listeners = listeners;
        logger = LoggerFactory.getLogger(getClass());
        currentMode = new ScanningMode(command, this, argumentPrefixConfig);
    }

    public void parse() {
        parse(command.getCommandBody());
    }

    public void parse(String input) {
        chars = input.toCharArray();
        for (; currentPosition < chars.length; currentPosition++) {
            char character = chars[currentPosition];
            Mode previousMode = currentMode;
            try {
                if (isEscaped) {
                    if (!checkLegalEscape(character)) {
                        throw new IllegalEscapeCharacterException("Illegal escape character at " + (currentPosition - 1));
                    }
                    currentMode = currentMode.handleLiteral(character);
                    isEscaped = false;
                } else if (isOpenQuotation) {
                    if (character == '\\') {
                        isEscaped = true;
                    } else if (character == '"') {
                        isOpenQuotation = false;
                    } else {
                        currentMode = currentMode.handleLiteral(character);
                    }
                } else {
                    if (character == '\\') {
                        isEscaped = true;
                    } else if (character == '"') {
                        isOpenQuotation = true;
                    } else {
                        currentMode = currentMode.handle(character);
                    }
                }

                if (currentPosition == chars.length - 1) {
                    if (isOpenQuotation) {
                        throw new UnclosedQuotationsException("Unclosed double quotes");
                    }
                    if (isEscaped) {
                        throw new IllegalEscapeCharacterException("Illegal trailing escape character");
                    }

                    currentMode.terminate();
                }
            } catch (CommandParseException e) {
                throw e;
            } catch (UserException e) {
                throw new CommandParseException(e.getMessage(), command.getCommandBody(), e, currentPosition);
            } catch (Exception e) {
                logger.error("Unexpected exception while parsing command", e);
                throw e;
            }

            // fire mode switch event even if it's a different instance of the same mode
            if (previousMode != currentMode) {
                fireOnModeSwitch(previousMode, currentMode, currentPosition, character);
            }
        }

        fireOnParseFinished();
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    /**
     * @return the next char to be parsed AND move the cursor, meaning it is skipped by the parser. Returns 0 if no such char.
     */
    public char getNextChar() {
        return charAtOrNull(++currentPosition);
    }

    /**
     * @return the next char to be parsed without moving the cursor. Returns 0 if no such char.
     */
    public char peekNextChar() {
        return charAtOrNull(currentPosition + 1);
    }

    public char charAtOrNull(int pos) {
        if (pos < chars.length) {
            return chars[pos];
        } else {
            return 0;
        }
    }

    void fireOnParseFinished() {
        emitEvent(listener -> listener.onParseFinished(command));
    }

    void fireOnModeSwitch(Mode previousMode, Mode newMode, int index, char character) {
        emitEvent(listener -> listener.onModeSwitch(previousMode, newMode, index, character));
    }

    void fireOnArgumentParsed(String argument, String value) {
        emitEvent(listener -> listener.onArgumentParsed(argument, value));
    }

    void fireOnCommandInputParsed(String commandInput) {
        emitEvent(listener -> listener.onCommandInputParsed(commandInput));
    }

    private void emitEvent(Consumer<CommandParseListener> event) {
        for (CommandParseListener listener : listeners) {
            try {
                event.accept(listener);
            } catch (Exception e) {
                logger.error("Error in CommandParseListener", e);
            }
        }
    }

    private boolean checkLegalEscape(char character) {
        return character == argumentPrefixConfig.getArgumentPrefix()
            || character == argumentPrefixConfig.getDefaultArgumentPrefix()
            || META.contains(character);
    }

    /**
     * Each parsed character is handled with a certain mode. A Mode defines what happens to an entered character,
     * whether it is part of an argument declaration or part of the command input.
     */
    public interface Mode {

        /**
         * Handles the currently parsed character. This method is sensitive to meta characters, meaning certain characters
         * might affect how the next character is parsed. For instance if the current character is '$' the next character
         * will be part of an argument declaration, if the current character is '"' it might open a quotation etc.
         *
         * @param character the current character of the command body
         * @return the mode to handle the next character with
         */
        Mode handle(char character);

        /**
         * Handles the current character as normal command input, ignoring meta characters. This method is called if the
         * the last character was an escape character or inside a quotation.
         *
         * @param character the current character
         * @return the mode to handle the next character with
         */
        Mode handleLiteral(char character);

        /**
         * Method called before switching to a new mode or after parsing the last character of the command. Signals the
         * mode to apply all characters that it recorded.
         */
        void terminate();

    }

}
