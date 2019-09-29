package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import net.robinfriedli.jxp.exec.AbstractDelegatingModeWrapper;
import net.robinfriedli.jxp.exec.Invoker;

/**
 * Mode that runs the given task with default Spotify credentials applied
 */
public class SpotifyAuthorizationMode extends AbstractDelegatingModeWrapper {

    private final SpotifyApi spotifyApi;

    public SpotifyAuthorizationMode(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    @Override
    public <E> Callable<E> wrap(Callable<E> callable) {
        return () -> {
            try {
                ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
                spotifyApi.setAccessToken(credentials.getAccessToken());

                return callable.call();
            } finally {
                spotifyApi.setAccessToken(null);
            }
        };
    }

    public Invoker.Mode getMode(SpotifyApi spotifyApi) {
        return Invoker.Mode.create().with(new SpotifyAuthorizationMode(spotifyApi));
    }

}
