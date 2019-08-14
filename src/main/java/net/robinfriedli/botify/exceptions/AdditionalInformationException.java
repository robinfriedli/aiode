package net.robinfriedli.botify.exceptions;

/**
 * Type of UserException that may provide additional information about the error
 */
public abstract class AdditionalInformationException extends UserException {

    public AdditionalInformationException() {
        super();
    }

    public AdditionalInformationException(String message) {
        super(message);
    }

    public AdditionalInformationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AdditionalInformationException(Throwable cause) {
        super(cause);
    }

    public abstract String getAdditionalInformation();

}
