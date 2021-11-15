package net.robinfriedli.aiode.audio.spotify;

import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.aiode.login.Login;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;

public class SpotifyContext {

    private ClientCredentials clientCredentials;
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

    public ClientCredentials getClientCredentials() {
        return clientCredentials;
    }

    public void setClientCredentials(ClientCredentials clientCredentials) {
        this.clientCredentials = clientCredentials;
    }
}
