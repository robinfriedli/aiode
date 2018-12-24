package net.robinfriedli.botify.login;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.exceptions.NoLoginException;
import net.robinfriedli.botify.util.UserMap;

public class LoginManager {

    private final SpotifyApi spotifyApi;
    private List<Login> logins = Lists.newArrayList();
    private UserMap<CompletableFuture<Login>> expectedLogins = new UserMap<>();

    public LoginManager(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    public void addLogin(Login login) {
        Login loginForUser = getLoginForUser(login.getUser());
        if (loginForUser != null) {
            logins.remove(loginForUser);
        }
        logins.add(login);
    }

    public CompletableFuture<Login> getPendingLogin(User user) {
        CompletableFuture<Login> pendingLogin = expectedLogins.get(user);

        if (pendingLogin == null) {
            throw new IllegalStateException("Unexpected login attempt for user " + user.getName());
        }

        return pendingLogin;
    }

    public void removePendingLogin(User user) {
        expectedLogins.remove(user);
    }

    public void expectLogin(User user, CompletableFuture<Login> futureLogin) {
        expectedLogins.put(user, futureLogin);
    }

    public Login requireLoginForUser(User user) throws NoLoginException {
        Login loginForUser = getLoginForUser(user);

        if (loginForUser != null) {
            return loginForUser;
        } else {
            throw new NoLoginException(user);
        }
    }

    @Nullable
    public Login getLoginForUser(User user) {
        List<Login> foundLogins = logins.stream()
            .filter(login -> !login.isExpired() && login.getUser().getId().equals(user.getId()))
            .collect(Collectors.toList());

        if (foundLogins.size() == 1) {
            return logins.get(0);
        } else if (foundLogins.size() > 1) {
            throw new IllegalStateException("Duplicate logins found for user " + user.getName());
        }

        return null;
    }
}
