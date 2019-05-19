package net.robinfriedli.botify.command.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandInterceptor;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.NoLoginException;
import net.robinfriedli.botify.exceptions.UserException;
import org.hibernate.Session;

public class CommandExecutionInterceptor implements CommandInterceptor {

    private final Logger logger;

    public CommandExecutionInterceptor() {
        this.logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void intercept(AbstractCommand command) {
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
            context.getGuildContext().getInvoker().invoke(session, () -> session.persist(history));
        } else {
            logger.warn("Command " + command + " has not history");
        }

    }

}
