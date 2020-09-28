package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import net.robinfriedli.exec.AbstractNestedModeWrapper;
import net.robinfriedli.exec.Mode;
import org.jetbrains.annotations.NotNull;

/**
 * Mode that runs the given task with default Spotify credentials applied
 */
public class SpotifyAuthorizationMode extends AbstractNestedModeWrapper {

    private final SpotifyApi spotifyApi;

    public SpotifyAuthorizationMode(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    @Override
    public <E> @NotNull Callable<E> wrap(@NotNull Callable<E> callable) {
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

    public Mode getMode(SpotifyApi spotifyApi) {
        return Mode.create().with(new SpotifyAuthorizationMode(spotifyApi));
    }

}
