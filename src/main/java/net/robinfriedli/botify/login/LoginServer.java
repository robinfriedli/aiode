package net.robinfriedli.botify.login;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

public class LoginServer {

    public LoginServer(JDA jda, SpotifyApi spotifyApi, LoginManager loginManager) throws IOException {
        int port = Integer.parseInt(PropertiesLoadingService.requireProperty("SERVER_PORT"));
        String loginContextPath = PropertiesLoadingService.requireProperty("LOGIN_CONTEXT_PATH");
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext(loginContextPath, new LoginHandler(jda, spotifyApi, loginManager));
        httpServer.setExecutor(null);
        httpServer.start();
    }

    public static LoginServer start(JDA jda, SpotifyApi spotifyApi, LoginManager loginManager) throws IOException {
        return new LoginServer(jda, spotifyApi, loginManager);
    }

    static class LoginHandler implements HttpHandler {

        private final JDA jda;
        private final SpotifyApi spotifyApi;
        private final LoginManager loginManager;

        LoginHandler(JDA jda, SpotifyApi spotifyApi, LoginManager loginManager) {
            this.jda = jda;
            this.spotifyApi = spotifyApi;
            this.loginManager = loginManager;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            try {
                String queryParameterString = getQueryString(httpExchange.getRequestURI().toASCIIString());
                Map<String, String> parameterMap = getQueryParameters(queryParameterString);

                String accessCode = parameterMap.get("code");
                String userId = parameterMap.get("state");

                User user = jda.getUserById(userId);
                if (user == null) {
                    throw new IllegalArgumentException("No user found for id " + userId);
                }

                CompletableFuture<Login> pendingLogin = loginManager.getPendingLogin(user);
                createLogin(accessCode, user, pendingLogin);

                String response = String.format("Welcome, %s", user.getName());
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (IllegalStateException | IllegalArgumentException | AssertionError | SpotifyWebApiException | IOException e) {
                String response = "Exception while handling request: " + e.getMessage();
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
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

        private String getQueryString(String requestUri) {
            StringList uriParts = StringListImpl.separateString(requestUri, "\\?");
            List<Integer> queryPartPositions = uriParts.findPositionsOf("\\?");

            int queryPartPos;
            if (queryPartPositions.size() == 1) {
                queryPartPos = queryPartPositions.get(0);
            } else {
                throw new IllegalArgumentException("Malformed request URI. Expected one \"?\" but found "
                    + queryPartPositions.size());
            }
            String queryParameterString = uriParts.tryGet(queryPartPos + 1);
            if (queryParameterString == null) {
                throw new IllegalArgumentException("No query parameters found");
            }

            return queryParameterString;
        }

        private Map<String, String> getQueryParameters(String queryParameterString) {
            StringList queryParameters = StringListImpl.create(queryParameterString, "&");
            queryParameters.assertThat(p -> p.size() == 2, "Expected 2 query parameters but found " + queryParameters.size());
            queryParameters.assertThat(p -> p.stream().allMatch(v -> v.startsWith("code=") || v.startsWith("state=")),
                "Unexpected query parameter found. Expected either \"code\" or \"state\".");

            Map<String, String> parameterMap = new HashMap<>();
            for (String queryParameter : queryParameters) {
                StringList keyWithValue = StringListImpl.create(queryParameter, "=");
                keyWithValue.assertThat(p -> p.size() == 2, "Parameter " + queryParameter + " has no value");
                parameterMap.put(keyWithValue.get(0), keyWithValue.get(1));
            }

            return parameterMap;
        }
    }

}
