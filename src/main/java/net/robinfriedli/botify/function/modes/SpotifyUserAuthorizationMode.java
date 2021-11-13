package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import net.robinfriedli.botify.login.Login;
import net.robinfriedli.exec.AbstractNestedModeWrapper;
import org.jetbrains.annotations.NotNull;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Mode that runs the given task with Spotify credentials for the given Login applied applied
 */
public class SpotifyUserAuthorizationMode extends AbstractNestedModeWrapper {

    private final Login login;
    private final SpotifyApi spotifyApi;

    public SpotifyUserAuthorizationMode(Login login, SpotifyApi spotifyApi) {
        this.login = login;
        this.spotifyApi = spotifyApi;
    }

    @Override
    public <E> @NotNull Callable<E> wrap(@NotNull Callable<E> callable) {
        return () -> {
            try {
                spotifyApi.setAccessToken(login.getAccessToken());
                return callable.call();
            } finally {
                spotifyApi.setAccessToken(null);
            }
        };
    }

}
