package net.robinfriedli.botify.exceptions;

/**
 * Exception class that signals that an undefined argument was used
 */
public class InvalidArgumentException extends AdditionalInformationException {

    public InvalidArgumentException() {
        super();
    }

    public InvalidArgumentException(String message) {
        super(message);
    }

    public InvalidArgumentException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidArgumentException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getAdditionalInformation() {
        return "Is this not supposed to be an argument? Botify interpreted it as one because it started with the argument" +
            " prefix ('$' or your custom argument prefix defined with the property command). If this was a mistake and the" +
            " argument prefix is supposed to be part of the command input you can escape the prefix by putting a `\\`" +
            " in front of it. E.g. `play $spotify \\$trackname`.";
    }
}
