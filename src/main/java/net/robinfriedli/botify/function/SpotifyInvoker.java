package net.robinfriedli.botify.function;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import com.wrapper.spotify.SpotifyApi;
import net.robinfriedli.botify.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.botify.function.modes.SpotifyUserAuthorizationMode;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.exec.BaseInvoker;
import net.robinfriedli.exec.Mode;
import org.jetbrains.annotations.NotNull;

/**
 * Invoker that runs a task with the appropriate Spotify credentials -> the credentials for the provided login or, if
 * absent, the default client credentials.
 */
public class SpotifyInvoker extends BaseInvoker {

    private final SpotifyApi spotifyApi;
    @Nullable
    private final Login login;

    public SpotifyInvoker(SpotifyApi spotifyApi) {
        this(spotifyApi, null);
    }

    public SpotifyInvoker(SpotifyApi spotifyApi, @Nullable Login login) {
        this.spotifyApi = spotifyApi;
        this.login = login;
    }

    public static SpotifyInvoker create(SpotifyApi spotifyApi) {
        return new SpotifyInvoker(spotifyApi);
    }

    public static SpotifyInvoker create(SpotifyApi spotifyApi, Login login) {
        return new SpotifyInvoker(spotifyApi, login);
    }

    @Override
    public <E> E invoke(@NotNull Mode mode, @NotNull Callable<E> task) throws Exception {
        if (login != null) {
            mode.with(new SpotifyUserAuthorizationMode(login, spotifyApi));
        } else {
            mode.with(new SpotifyAuthorizationMode(spotifyApi));
        }
        return super.invoke(mode, task);
    }

    public <E> E invoke(Callable<E> task) throws Exception {
        return invoke(Mode.create(), task);
    }

}
