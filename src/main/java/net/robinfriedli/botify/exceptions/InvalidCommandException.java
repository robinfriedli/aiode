package net.robinfriedli.botify.exceptions;

/**
 * Type of command thrown when the cause is user error. This exception typically gets caught and its message sent to
 * Discord.
 */
public class InvalidCommandException extends UserException {

    public InvalidCommandException() {
        super();
    }

    public InvalidCommandException(String errorMessage) {
        super(enhanceMessage(errorMessage));
    }

    public InvalidCommandException(Throwable cause) {
        super(cause);
    }

    public InvalidCommandException(String errorMessage, Throwable cause) {
        super(enhanceMessage(errorMessage), cause);
    }

    private static String enhanceMessage(String message) {
        return message + System.lineSeparator() + "If you need help with a command you can use the help command. E.g. $botify help play";
    }

}
