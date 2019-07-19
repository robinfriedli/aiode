package net.robinfriedli.botify.login;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.exceptions.InvalidRequestException;
import net.robinfriedli.botify.servers.ServerUtil;
import net.robinfriedli.botify.util.PropertiesLoadingService;

public class LoginHandler implements HttpHandler {

    private final JDA jda;
    private final SpotifyApi spotifyApi;
    private final LoginManager loginManager;

    public LoginHandler(JDA jda, SpotifyApi spotifyApi, LoginManager loginManager) {
        this.jda = jda;
        this.spotifyApi = spotifyApi;
        this.loginManager = loginManager;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String loginPagePath = PropertiesLoadingService.requireProperty("LOGIN_PAGE_PATH");
        String html = Files.readString(Path.of(loginPagePath));
        try {
            Map<String, String> parameterMap = ServerUtil.getParameters(httpExchange);

            String accessCode = parameterMap.get("code");
            String userId = parameterMap.get("state");
            String error = parameterMap.get("error");

            String response;
            if (accessCode != null) {
                User user = jda.getUserById(userId);
                if (user == null) {
                    throw new InvalidRequestException("No user found for id " + userId);
                }

                CompletableFuture<Login> pendingLogin = loginManager.getPendingLogin(user);
                createLogin(accessCode, user, pendingLogin);

                response = String.format(html, "Welcome, " + user.getName());
            } else if (error != null) {
                response = String.format(html, "<span style=\"color: red\">Error:</span> " + error);
            } else {
                throw new InvalidRequestException("Missing parameter code or error");
            }

            httpExchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } catch (InvalidRequestException e) {
            ServerUtil.handleError(httpExchange, e);
        } catch (Throwable e) {
            ServerUtil.handleError(httpExchange, e);
            LoggerFactory.getLogger(getClass()).error("Error in HttpHandler", e);
        }
    }

    private void createLogin(String accessCode, User user, CompletableFuture<Login> pendingLogin) throws SpotifyWebApiException, IOException {
        AuthorizationCodeCredentials credentials = spotifyApi.authorizationCode(accessCode).build().execute();
        String accessToken = credentials.getAccessToken();
        String refreshToken = credentials.getRefreshToken();
        Integer expiresIn = credentials.getExpiresIn();

        Login login = new Login(user, accessToken, refreshToken, expiresIn, spotifyApi);
        loginManager.addLogin(login);
        loginManager.removePendingLogin(user);
        pendingLogin.complete(login);
    }
}
