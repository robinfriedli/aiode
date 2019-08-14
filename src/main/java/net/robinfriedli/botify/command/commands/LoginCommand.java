package net.robinfriedli.botify.command.commands;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.wrapper.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.botify.login.LoginManager;

public class LoginCommand extends AbstractCommand {

    public LoginCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, commandContext, commandManager, commandString, false, identifier, description, Category.SPOTIFY);
    }

    @Override
    public void doRun() {
        User user = getContext().getUser();
        AuthorizationCodeUriRequest uriRequest = getContext().getSpotifyApi().authorizationCodeUri()
            .show_dialog(true)
            .state(user.getId())
            .scope("playlist-read-private playlist-read-collaborative user-library-read playlist-modify-private playlist-modify-public")
            .build();

        LoginManager loginManager = Botify.get().getLoginManager();
        CompletableFuture<Login> pendingLogin = new CompletableFuture<>();
        loginManager.expectLogin(user, pendingLogin);

        String response = String.format("Your login link:\n%s", uriRequest.execute().toString());
        CompletableFuture<Message> futureMessage = sendMessage(user, response);
        try {
            futureMessage.get();
            sendMessage("I sent you a login link");
        } catch (CancellationException | ExecutionException e) {
            sendError("I was unable to send you a message. Please adjust your privacy settings to allow direct messages from guild members.");
        } catch (InterruptedException ignored) {
        }

        // we do not want to send a "Still loading..." message when waiting for login to complete
        getContext().interruptMonitoring();
        try {
            pendingLogin.get(10, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException e) {
            loginManager.removePendingLogin(user);
            throw new CommandRuntimeException(e);
        } catch (TimeoutException e) {
            loginManager.removePendingLogin(user);
            sendMessage(user, "Login attempt timed out");
            setFailed(true);
        }
    }

    @Override
    public void onSuccess() {
        sendSuccess("User " + getContext().getUser().getName() + " logged in to Spotify");
    }

}
