package net.robinfriedli.botify.exceptions;

/**
 * Exception that indicates that a user is submitting tasks too quickly.
 */
public class RateLimitException extends RuntimeException {

    private final boolean isTimeout;

    public RateLimitException(boolean isTimeout) {
        super();
        this.isTimeout = isTimeout;
    }

    public RateLimitException(boolean isTimeout, String message) {
        super(message);
        this.isTimeout = isTimeout;
    }

    public RateLimitException(boolean isTimeout, String message, Throwable cause) {
        super(message, cause);
        this.isTimeout = isTimeout;
    }

    public RateLimitException(boolean isTimeout, Throwable cause) {
        super(cause);
        this.isTimeout = isTimeout;
    }

    /**
     * @return true if the exception was caused by the client having previously hit the rate limit and trying to submit
     * a task again while the time out is still active
     */
    public boolean isTimeout() {
        return isTimeout;
    }
}
