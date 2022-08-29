package net.robinfriedli.aiode.function;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.spotify.SpotifyContext;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.exceptions.CommandRuntimeException;
import net.robinfriedli.aiode.function.modes.SpotifyAuthorizationMode;
import net.robinfriedli.aiode.function.modes.SpotifyMarketMode;
import net.robinfriedli.aiode.function.modes.SpotifyUserAuthorizationMode;
import net.robinfriedli.aiode.login.Login;
import net.robinfriedli.exec.BaseInvoker;
import net.robinfriedli.exec.Mode;
import org.jetbrains.annotations.NotNull;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Invoker that runs a task with the appropriate Spotify credentials -> the credentials for the provided login or, if
 * absent, the default client credentials.
 */
public class SpotifyInvoker extends BaseInvoker implements FunctionInvoker<SpotifyApi> {

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

    public static SpotifyInvoker createForCurrentContext() {
        SpotifyApi.Builder spotifyApiBuilder = Aiode.get().getSpotifyApiBuilder();
        Optional<SpotifyContext> installedSpotifyContext = ThreadContext.Current.optional(SpotifyContext.class);
        SpotifyApi spotifyApi = installedSpotifyContext
            .map(SpotifyContext::getSpotifyApi)
            .orElseGet(spotifyApiBuilder::build);
        if (installedSpotifyContext.isPresent()) {
            SpotifyContext spotifyContext = installedSpotifyContext.get();
            return new SpotifyInvoker(spotifyApi, spotifyContext.getLogin(), spotifyContext.getMarket());
        }
        return create(spotifyApi);
    }

    @Override
    public <E> E invoke(@NotNull Mode mode, @NotNull Callable<E> callable) {
        if (login != null) {
            mode = mode.with(new SpotifyUserAuthorizationMode(login, spotifyApi));
        } else {
            mode = mode.with(new SpotifyAuthorizationMode(spotifyApi));
        }

        if (market != null) {
            mode = mode.with(new SpotifyMarketMode(market));
        }

        try {
            return super.invoke(mode, callable);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        }
    }

    @Override
    public <V> V invokeFunction(Function<SpotifyApi, V> function) {
        return invokeFunction(Mode.create(), function);
    }

    @Override
    public <V> V invokeFunction(Mode mode, Function<SpotifyApi, V> function) {
        return invoke(mode, () -> function.apply(spotifyApi));
    }
}
