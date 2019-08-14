package net.robinfriedli.botify.exceptions;

public class UnclosedQuotationsException extends AdditionalInformationException {

    public UnclosedQuotationsException() {
        super();
    }

    public UnclosedQuotationsException(String message) {
        super(message);
    }

    public UnclosedQuotationsException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnclosedQuotationsException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getAdditionalInformation() {
        return "If you need to use a quotation mark in your command without the bot treating it as a quotation but a " +
            "normal character you can escape the quotation mark by putting a `\\` in front of it. " +
            "E.g. `play tr\\\"ack`.";
    }
}
