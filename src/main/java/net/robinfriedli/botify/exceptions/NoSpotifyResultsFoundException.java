package net.robinfriedli.botify.exceptions;

public class NoSpotifyResultsFoundException extends NoResultsFoundException {

    public NoSpotifyResultsFoundException() {
        super();
    }

    public NoSpotifyResultsFoundException(String errorMessage) {
        super(enhanceMessage(errorMessage));
    }

    public NoSpotifyResultsFoundException(Throwable cause) {
        super(cause);
    }

    public NoSpotifyResultsFoundException(String errorMessage, Throwable cause) {
        super(enhanceMessage(errorMessage), cause);
    }

    private static String enhanceMessage(String message) {
        return (message.length() > 1000 ? message.substring(0, 1000) + "..." : message) + System.lineSeparator() + System.lineSeparator() +
            "_Mind that Spotify queries, unlike YouTube, are not a fulltext search so you shouldn't type 'numb by linkin park' " +
            "but rather use the appropriate filters; e.g. 'numb artist:linkin park'. If you didn't actually mean to search " +
            "for a Spotify track but rather a playlist or YouTube video, see 'help play' to find the needed arguments or " +
            "adjust the default source using the property command._";
    }

}
