package net.robinfriedli.aiode.audio.spotify;

import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.aiode.login.Login;
import se.michaelthelin.spotify.SpotifyApi;

public class SpotifyContext {

    private SpotifyApi spotifyApi;
    private CountryCode market;
    private Login login;

    public CountryCode getMarket() {
        return market;
    }

    public void setMarket(CountryCode market) {
        this.market = market;
    }

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login;
    }

    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

    public void setSpotifyApi(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }
}
