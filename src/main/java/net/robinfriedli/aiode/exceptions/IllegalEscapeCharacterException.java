package net.robinfriedli.aiode.exceptions;

public class IllegalEscapeCharacterException extends AdditionalInformationException {

    public IllegalEscapeCharacterException() {
        super();
    }

    public IllegalEscapeCharacterException(String message) {
        super(message);
    }

    public IllegalEscapeCharacterException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalEscapeCharacterException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getAdditionalInformation() {
        return "The escape character (`\\`) is used to indicate that a character that normally triggers an action (such as " +
            "the argument prefix '$' (makes the bot parse an argument), whitespace ' ' (may cause the bot to stop parsing " +
            "an argument) or double quotes '\"' (may cause the bot to start / stop parsing an argument value)) should be " +
            "treated as normal command input instead. So if you need to use the character `\\` in your command you need to " +
            "escape it by adding a second `\\` to treat it as a normal character. E.g. wrong: `play tr\\ack`, correct: " +
            "`play tr\\\\ack`.";
    }
}
