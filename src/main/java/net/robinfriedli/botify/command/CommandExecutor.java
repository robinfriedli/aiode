package net.robinfriedli.botify.command;

import java.util.concurrent.Callable;

import org.slf4j.Logger;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.discord.AlertService;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoLoginException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.botify.login.LoginManager;

/**
 * responsible for executing a command based on whether it requires a login or client credentials
 */
public class CommandExecutor {

    private final SpotifyApi spotifyApi;
    private final LoginManager loginManager;
    private final Logger logger;

    public CommandExecutor(SpotifyApi spotifyApi, LoginManager loginManager, Logger logger) {
        this.spotifyApi = spotifyApi;
        this.loginManager = loginManager;
        this.logger = logger;
    }

    public void runCommand(AbstractCommand command) {
        try {
            command.verify();
            if (command.requiresLogin()) {
                User user = command.getContext().getUser();
                Login login = loginManager.requireLoginForUser(user);
                spotifyApi.setAccessToken(login.getAccessToken());
            } else if (command.requiresClientCredentials()) {
                ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
                spotifyApi.setAccessToken(credentials.getAccessToken());
            }
            command.doRun();
            if (!command.isFailed()) {
                command.onSuccess();
            }
        } catch (NoLoginException e) {
            AlertService alertService = new AlertService(logger);
            MessageChannel channel = command.getContext().getChannel();
            User user = command.getContext().getUser();
            alertService.send("User " + user.getName() + " is not logged in to Spotify", channel);
        } catch (InvalidCommandException | NoResultsFoundException | ForbiddenCommandException e) {
            AlertService alertService = new AlertService(logger);
            alertService.send(e.getMessage(), command.getContext().getChannel());
        } catch (UnauthorizedException e) {
            AlertService alertService = new AlertService(logger);
            alertService.send("Unauthorized: " + e.getMessage(), command.getContext().getChannel());
        } catch (CommandRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandRuntimeException(e);
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

    public <E> E runForUser(User user, Callable<E> callable) throws Exception {
        try {
            Login loginForUser = loginManager.requireLoginForUser(user);
            spotifyApi.setAccessToken(loginForUser.getAccessToken());
            return callable.call();
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

    public <E> E runWithCredentials(Callable<E> callable) throws Exception {
        try {
            ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
            spotifyApi.setAccessToken(credentials.getAccessToken());

            return callable.call();
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

}
