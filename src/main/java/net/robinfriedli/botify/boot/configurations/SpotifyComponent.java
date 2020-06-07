package net.robinfriedli.botify.boot.configurations;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class SpotifyComponent {

    @Value("${botify.tokens.spotify_client_id}")
    private String spotifyClientId;
    @Value("${botify.tokens.spotify_client_secret}")
    private String spotifyClientSecret;
    @Value("${botify.server.spotify_login_callback}")
    private String redirectUri;
    @Value("${botify.preferences.spotify_market}")
    private String defaultMarket;

    @Bean
    public SpotifyApi.Builder spotifyApiBuilder() {
        return new SpotifyApi.Builder()
            .setClientId(spotifyClientId)
            .setClientSecret(spotifyClientSecret)
            .setRedirectUri(SpotifyHttpManager.makeUri(redirectUri));
    }

    @Bean
    @Scope(scopeName = "prototype")
    public SpotifyApi spotifyApi(SpotifyApi.Builder builder) {
        return builder.build();
    }

    public String getDefaultMarket() {
        return defaultMarket;
    }
}
