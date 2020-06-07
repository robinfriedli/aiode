package net.robinfriedli.botify.function.modes;

import java.util.concurrent.Callable;

import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.botify.audio.spotify.SpotifyContext;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.jxp.exec.AbstractDelegatingModeWrapper;

/**
 * Set the Spotify market used for requests in the current {@link SpotifyContext}.
 */
public class SpotifyMarketMode extends AbstractDelegatingModeWrapper {

    private final CountryCode market;

    public SpotifyMarketMode(CountryCode market) {
        this.market = market;
    }

    @Override
    public <E> Callable<E> wrap(Callable<E> callable) {
        return () -> {
            SpotifyContext spotifyContext = ThreadContext.Current.get().getOrCompute(SpotifyContext.class, SpotifyContext::new);
            spotifyContext.setMarket(market);
            return callable.call();
        };
    }
}
