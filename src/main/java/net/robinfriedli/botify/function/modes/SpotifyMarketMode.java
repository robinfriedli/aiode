package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.botify.audio.spotify.SpotifyContext;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.exec.AbstractNestedModeWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Set the Spotify market used for requests in the current {@link SpotifyContext}.
 */
public class SpotifyMarketMode extends AbstractNestedModeWrapper {

    private final CountryCode market;

    public SpotifyMarketMode(CountryCode market) {
        this.market = market;
    }

    @Override
    public <E> @NotNull Callable<E> wrap(@NotNull Callable<E> callable) {
        return () -> {
            SpotifyContext spotifyContext = ThreadContext.Current.get().getOrCompute(SpotifyContext.class, SpotifyContext::new);
            spotifyContext.setMarket(market);
            return callable.call();
        };
    }
}
