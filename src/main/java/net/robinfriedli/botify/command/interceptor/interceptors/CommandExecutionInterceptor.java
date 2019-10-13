package net.robinfriedli.botify.command.interceptor.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Strings;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.exceptions.AmbiguousCommandException;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.NoLoginException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.function.HibernateInvoker;
import org.hibernate.Session;

/**
 * CommandInterceptor that runs the commands logic by calling {@link Command#doRun()} and handles exceptions thrown
 * during execution
 */
public class CommandExecutionInterceptor implements CommandInterceptor {

    private final MessageService messageService;
    private final Logger logger;

    public CommandExecutionInterceptor(MessageService messageService) {
        this.messageService = messageService;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void intercept(Command command) {
        boolean completedSuccessfully = false;
        boolean failedManually = false;
        String errorMessage = null;
        boolean unexpectedException = false;
        try {
            try {
                command.doRun();
            } catch (Throwable e) {
                command.onFailure();
                throw e;
            }
            if (!command.isFailed()) {
                command.onSuccess();
                completedSuccessfully = true;
            } else {
                command.onFailure();
                failedManually = true;
            }
        } catch (AmbiguousCommandException e) {
            if (command instanceof AbstractCommand) {
                ((AbstractCommand) command).askQuestion(e.getOptions(), e.getDisplayFunc());
            } else {
                throw e;
            }
        } catch (NoLoginException e) {
            MessageChannel channel = command.getContext().getChannel();
            User user = command.getContext().getUser();
            String message = "User " + user.getName() + " is not logged in to Spotify";
            messageService.sendError(message, channel);
            errorMessage = message;
        } catch (UserException e) {
            messageService.sendTemporary(e.buildEmbed().build(), command.getContext().getChannel());
            errorMessage = e.getMessage();
        } catch (UnauthorizedException e) {
            String message = "Unauthorized: " + e.getMessage();
            messageService.sendException(message, command.getContext().getChannel());
            logger.warn("Unauthorized Spotify API operation", e);
            errorMessage = message;
            unexpectedException = true;
        } catch (TooManyRequestsException e) {
            String message = "Executing too many Spotify requests at the moment, please try again later.";
            messageService.sendException(message,
                command.getContext().getChannel());
            logger.warn("Executing too many Spotify requests", e);
            errorMessage = message;
            unexpectedException = true;
        } catch (GoogleJsonResponseException e) {
            String message = e.getDetails().getMessage();
            StringBuilder responseBuilder = new StringBuilder("Error occurred when requesting data from YouTube.");
            if (!Strings.isNullOrEmpty(message)) {
                responseBuilder.append(" Error response: ").append(message);
            }
            messageService.sendException(responseBuilder.toString(), command.getContext().getChannel());
            logger.error("Exception during YouTube request", e);
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

    /**
     * Finalize and persist the {@link CommandHistory} entry for this command execution.
     *
     * @param command               the executed command
     * @param completedSuccessfully whether the command completed sucessfully
     * @param failedManually        whether the command failed because {@link Command#isFailed()} returned true
     * @param errorMessage          the error message if an exception was thrown
     * @param unexpectedException   whether an unexpected exception was thrown, this excludes {@link UserException}
     */
    private void postCommand(Command command,
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
            HibernateInvoker.create().invoke(() -> session.persist(history));
        } else {
            logger.warn("Command " + command + " has no history");
        }

    }

}
