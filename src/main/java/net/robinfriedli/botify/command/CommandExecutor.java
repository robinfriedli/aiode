package net.robinfriedli.botify.command;

import java.util.concurrent.Callable;

import org.slf4j.Logger;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import com.wrapper.spotify.model_objects.credentials.ClientCredentials;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.NoLoginException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.util.CheckedRunnable;
import org.hibernate.Session;

/**
 * responsible for executing a command based
 */
public class CommandExecutor {

    private final LoginManager loginManager;
    private final Logger logger;

    public CommandExecutor(LoginManager loginManager, Logger logger) {
        this.loginManager = loginManager;
        this.logger = logger;
    }

    public void runCommand(AbstractCommand command) {
        boolean completedSuccessfully = false;
        boolean failedManually = false;
        String errorMessage = null;
        boolean unexpectedException = false;
        try {
            command.doRun();
            if (!command.isFailed()) {
                command.onSuccess();
                completedSuccessfully = true;
            } else {
                failedManually = true;
            }
        } catch (NoLoginException e) {
            MessageService messageService = new MessageService();
            MessageChannel channel = command.getContext().getChannel();
            User user = command.getContext().getUser();
            String message = "User " + user.getName() + " is not logged in to Spotify";
            messageService.sendError(message, channel);
            errorMessage = message;
        } catch (UserException e) {
            MessageService messageService = new MessageService();
            messageService.sendError(e.getMessage(), command.getContext().getChannel());
            errorMessage = e.getMessage();
        } catch (UnauthorizedException e) {
            MessageService messageService = new MessageService();
            String message = "Unauthorized: " + e.getMessage();
            messageService.sendException(message, command.getContext().getChannel());
            logger.warn("Unauthorized Spotify API operation", e);
            errorMessage = message;
            unexpectedException = true;
        } catch (TooManyRequestsException e) {
            MessageService messageService = new MessageService();
            String message = "Executing too many Spotify requests at the moment, please try again later.";
            messageService.sendException(message,
                command.getContext().getChannel());
            logger.warn("Executing too many Spotify requests", e);
            errorMessage = message;
            unexpectedException = true;
        } catch (CommandRuntimeException e) {
            if (e.getCause() != null) {
                errorMessage = e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            } else {
                errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            unexpectedException = true;
            throw e;
        } catch (Throwable e) {
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            unexpectedException = true;
            throw new CommandRuntimeException(e);
        } finally {
            try {
                postCommand(command, completedSuccessfully, failedManually, errorMessage, unexpectedException);
            } catch (Throwable e) {
                logger.error("Exception in postCommand ", e);
            }
        }
    }

    private void postCommand(AbstractCommand command,
                             boolean completedSuccessfully,
                             boolean failedManually,
                             String errorMessage,
                             boolean unexpectedException) {
        CommandContext context = command.getContext();
        context.interruptMonitoring();
        CommandHistory history = context.getCommandHistory();
        if (history != null) {
            history.setDurationMs(System.currentTimeMillis() - history.getStartMillis());
            history.setCompletedSuccessfully(completedSuccessfully);
            history.setFailedManually(failedManually);
            history.setUnexpectedException(unexpectedException);
            history.setErrorMessage(errorMessage);

            Session session = context.getSession();
            invoke(session, () -> session.persist(history));
        } else {
            logger.warn("Command " + command + " has not history");
        }

    }

    public void invoke(Session session, CheckedRunnable runnable) {
        invoke(session, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Invoke a callable in a hibernate transaction.
     *
     * @param session the target hibernate session, individual for each command execution
     * @param callable tho callable to run
     * @param <E> the return type
     * @return the value the callable returns, often void
     */
    public <E> E invoke(Session session, Callable<E> callable) {
        boolean isNested = false;
        if (session.getTransaction() == null || !session.getTransaction().isActive()) {
            session.beginTransaction();
        } else {
            isNested = true;
        }
        if (isNested) {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CommandRuntimeException(e);
            }
        }
        E retVal;
        try {
            retVal = callable.call();
            session.getTransaction().commit();
        } catch (UserException e) {
            session.getTransaction().rollback();
            throw e;
        } catch (Exception e) {
            session.getTransaction().rollback();
            throw new RuntimeException("Exception in invoked callable. Transaction rolled back.", e);
        }
        return retVal;
    }

    public <E> E runForUser(User user, SpotifyApi spotifyApi, Callable<E> callable) throws Exception {
        try {
            Login loginForUser = loginManager.requireLoginForUser(user);
            spotifyApi.setAccessToken(loginForUser.getAccessToken());
            return callable.call();
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

    public <E> E runWithCredentials(SpotifyApi spotifyApi, Callable<E> callable) throws Exception {
        try {
            ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
            spotifyApi.setAccessToken(credentials.getAccessToken());

            return callable.call();
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

}
