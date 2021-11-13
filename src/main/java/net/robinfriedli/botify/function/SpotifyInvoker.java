package net.robinfriedli.botify.function;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.botify.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.botify.function.modes.SpotifyMarketMode;
import net.robinfriedli.botify.function.modes.SpotifyUserAuthorizationMode;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.exec.BaseInvoker;
import net.robinfriedli.exec.Mode;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Invoker that runs a task with the appropriate Spotify credentials -> the credentials for the provided login or, if
 * absent, the default client credentials.
 */
public class SpotifyInvoker extends BaseInvoker {

    private final SpotifyApi spotifyApi;
    @Nullable
    private final Login login;
    @Nullable
    private final CountryCode market;

    public SpotifyInvoker(SpotifyApi spotifyApi) {
        this(spotifyApi, null);
    }

    public SpotifyInvoker(SpotifyApi spotifyApi, @Nullable Login login) {
        this(spotifyApi, login, null);
    }

    public SpotifyInvoker(SpotifyApi spotifyApi, @Nullable Login login, @Nullable CountryCode market) {
        this.spotifyApi = spotifyApi;
        this.login = login;
        this.market = market;
    }


    public static SpotifyInvoker create(SpotifyApi spotifyApi) {
        return new SpotifyInvoker(spotifyApi);
    }

    public static SpotifyInvoker create(SpotifyApi spotifyApi, String marketCountryCode) {
        return new SpotifyInvoker(spotifyApi, null, CountryCode.valueOf(marketCountryCode));
    }

    public static SpotifyInvoker create(SpotifyApi spotifyApi, Login login) {
        return new SpotifyInvoker(spotifyApi, login);
    }

    public static SpotifyInvoker create(SpotifyApi spotifyApi, Login login, String marketCountryCode) {
        return new SpotifyInvoker(spotifyApi, login, CountryCode.valueOf(marketCountryCode));
    }

    public <E> E invoke(Callable<E> task) throws Exception {
        Mode mode = Mode.create();
        if (login != null) {
            mode.with(new SpotifyUserAuthorizationMode(login, spotifyApi));
        } else {
            mode.with(new SpotifyAuthorizationMode(spotifyApi));
        }

        if (market != null) {
            return invoke(mode.with(new SpotifyMarketMode(market)), task);
        }
        return invoke(mode, task);
    }

}
