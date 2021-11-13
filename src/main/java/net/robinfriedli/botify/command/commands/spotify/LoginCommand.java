package net.robinfriedli.botify.command.commands.spotify;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.CompletableFutures;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.botify.login.LoginManager;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;

public class LoginCommand extends AbstractCommand {

    public LoginCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, commandContext, commandManager, commandString, requiresInput, identifier, description, category);
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

        String loginUri = uriRequest.execute().toString();
        EmbedBuilder loginLinkBuilder = new EmbedBuilder()
            .setTitle("Spotify login")
            .setDescription(String.format("Click [here](%s) to be redirected to Spotify", loginUri))
            .setColor(0x1DB954);
        CompletableFuture<Message> futurePrivateMessage = getMessageService().send(loginLinkBuilder.build(), user);
        CompletableFuture<Message> futureNoticeMessage = new CompletableFuture<>();
        try {
            futurePrivateMessage.get();
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setDescription("I have sent you a login link");
            sendMessage(embedBuilder).thenAccept(futureNoticeMessage::complete);
        } catch (CancellationException | ExecutionException e) {
            loginManager.removePendingLogin(user);
            throw new UserException("I was unable to send you a message. Please adjust your privacy settings to allow direct messages from guild members.");
        } catch (InterruptedException ignored) {
        }

        CompletableFuture<Login> futureLogin = pendingLogin.orTimeout(10, TimeUnit.MINUTES);
        CompletableFutures.handleWhenComplete(futureLogin, (login, throwable) -> {
            futureNoticeMessage.thenAccept(message -> message.delete().queue());
            futurePrivateMessage.thenAccept(message -> message.delete().queue());
            if (login != null) {
                getMessageService().sendSuccess("You have successfully connected your Spotify account and may now search and play tracks from your library", user);
                sendSuccess("User " + getContext().getUser().getName() + " logged in to Spotify");
            }
            if (throwable != null) {
                loginManager.removePendingLogin(user);

                if (throwable instanceof TimeoutException) {
                    getMessageService().sendError("Login attempt timed out", user);
                } else {
                    getMessageService().sendException("There has been an unexpected error while completing your login, please try again.", getContext().getChannel());
                    LoggerFactory.getLogger(getClass()).error("unexpected exception while completing login", throwable);
                }
                setFailed(true);
            }
        }, e -> LoggerFactory.getLogger(getClass()).error("Unexpected error in whenComplete of pending login handler", e));
    }

    @Override
    public void onSuccess() {
    }

}
