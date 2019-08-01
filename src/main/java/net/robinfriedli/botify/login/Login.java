package net.robinfriedli.botify.login;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import net.dv8tion.jda.core.entities.User;

/**
 * Represents and automatically refreshes Spotify logins
 */
public class Login {

    private final User user;
    private final Timer refreshTimer;
    private String accessToken;
    private String refreshToken;
    private boolean expired = false;

    public Login(User user, String accessToken, String refreshToken, int expiresIn, SpotifyApi spotifyApi) {
        this.user = user;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.refreshTimer = new Timer();
        refreshTimer.schedule(new AutoRefreshTask(spotifyApi), expiresIn * 1000);
    }

    public User getUser() {
        return user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isExpired() {
        return expired;
    }

    public void expire() {
        expired = true;
    }

    public void cancel() {
        refreshTimer.cancel();
        expire();
    }

    private class AutoRefreshTask extends TimerTask {

        private final SpotifyApi spotifyApi;

        private AutoRefreshTask(SpotifyApi spotifyApi) {
            this.spotifyApi = spotifyApi;
        }

        @Override
        public void run() {
            try {
                spotifyApi.setRefreshToken(getRefreshToken());
                AuthorizationCodeCredentials refreshCredentials = spotifyApi.authorizationCodeRefresh().build().execute();
                setAccessToken(refreshCredentials.getAccessToken());

                refreshTimer.schedule(new AutoRefreshTask(spotifyApi), refreshCredentials.getExpiresIn() * 1000);
            } catch (IOException | SpotifyWebApiException e) {
                Logger logger = LoggerFactory.getLogger(getClass());
                logger.warn("Failed to refresh login for user " + user.getName(), e);
                expire();
            }
        }
    }
}
