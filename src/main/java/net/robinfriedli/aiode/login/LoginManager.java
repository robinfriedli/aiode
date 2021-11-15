package net.robinfriedli.aiode.login;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.exceptions.NoLoginException;
import net.robinfriedli.aiode.util.SnowflakeMap;
import org.springframework.stereotype.Component;

/**
 * Manages all Spotify logins
 */
@Component
public class LoginManager {

    private final SnowflakeMap<Login> logins = new SnowflakeMap<>();
    private final SnowflakeMap<CompletableFuture<Login>> expectedLogins = new SnowflakeMap<>();

    public void addLogin(Login login) {
        logins.put(login.getUser(), login);
    }

    public void removeLogin(User user) {
        logins.remove(user);
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
        Login login = logins.get(user);
        if (login != null && !login.isExpired()) {
            return login;
        }

        return null;
    }
}
