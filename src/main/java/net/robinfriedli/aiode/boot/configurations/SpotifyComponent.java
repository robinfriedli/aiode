package net.robinfriedli.aiode.boot.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;

@Configuration
public class SpotifyComponent {

    @Value("${aiode.tokens.spotify_client_id}")
    private String spotifyClientId;
    @Value("${aiode.tokens.spotify_client_secret}")
    private String spotifyClientSecret;
    @Value("${aiode.server.spotify_login_callback}")
    private String redirectUri;
    @Value("${aiode.preferences.spotify_market}")
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
