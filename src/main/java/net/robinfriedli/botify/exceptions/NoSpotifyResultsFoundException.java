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
        return (message.length() > 1000 ? message.substring(0, 1000) + "..." : message) + System.lineSeparator() +
            "Hint: When searching for a Spotify track it is not a fulltext search, meaning you should only write the name of the track. " +
            "If you want to limit the search to a specific artist or album you should do so at the end of the search query " +
            "and use the appropriate filters. E.g. $botify play numb artist:linkin park album:meteora. " +
            "If you cannot find the track on Spotify you can try searching YouTube using the $youtube argument ($botify play $youtube numb).";
    }

}
