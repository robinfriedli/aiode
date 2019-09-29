package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import com.wrapper.spotify.SpotifyApi;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.jxp.exec.AbstractDelegatingModeWrapper;

/**
 * Mode that runs the given task with Spotify credentials for the given Login applied applied
 */
public class SpotifyUserAuthorizationMode extends AbstractDelegatingModeWrapper {

    private final Login login;
    private final SpotifyApi spotifyApi;

    public SpotifyUserAuthorizationMode(Login login, SpotifyApi spotifyApi) {
        this.login = login;
        this.spotifyApi = spotifyApi;
    }

    @Override
    public <E> Callable<E> wrap(Callable<E> callable) {
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
