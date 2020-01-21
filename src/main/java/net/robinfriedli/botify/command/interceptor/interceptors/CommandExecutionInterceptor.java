package net.robinfriedli.botify.command.interceptor.interceptors;

import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Strings;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.exceptions.detailed.UnauthorizedException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.concurrent.LoggingThreadFactory;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.exceptions.Abort;
import net.robinfriedli.botify.exceptions.AmbiguousCommandException;
import net.robinfriedli.botify.exceptions.CommandFailure;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.NoLoginException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.persist.StaticSessionProvider;

/**
 * CommandInterceptor that runs the commands logic by calling {@link Command#doRun()} and handles exceptions thrown
 * during execution
 */
public class CommandExecutionInterceptor implements CommandInterceptor {

    private static final ExecutorService COMMAND_HISTORY_SERVICE = Executors.newFixedThreadPool(3, new LoggingThreadFactory("command-history-pool"));

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(COMMAND_HISTORY_SERVICE));
    }

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
                if (command.getThread().isTerminated()) {
                    logger.warn(String.format("Suppressed '%s' because command execution was interrupted.", e));
                    return;
                }

                try {
                    command.onFailure();
                } catch (Throwable e1) {
                    logger.error("Exception thrown in onFailure of command, logging this error and throwing the exception that caused the command to fail.", e1);
                }
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
        } catch (Abort ignored) {
        } catch (NoLoginException e) {
            MessageChannel channel = command.getContext().getChannel();
            User user = command.getContext().getUser();
            String message = "User " + user.getName() + " is not logged in to Spotify";
            messageService.sendError(message, channel);
            errorMessage = message;
            throw new CommandFailure(e);
        } catch (UserException e) {
            messageService.sendTemporary(e.buildEmbed().build(), command.getContext().getChannel());
            errorMessage = e.getMessage();
            throw new CommandFailure(e);
        } catch (FriendlyException e) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Could not load track");
            if (e.getMessage() != null) {
                embedBuilder.setDescription("Message returned by source: " + e.getMessage());
            }
            embedBuilder.setColor(Color.RED);

            messageService.sendTemporary(embedBuilder.build(), command.getContext().getChannel());
            throw new CommandFailure(e);
        } catch (UnauthorizedException e) {
            String message = "Unauthorized: " + e.getMessage();
            messageService.sendException(message, command.getContext().getChannel());
            logger.warn("Unauthorized Spotify API operation", e);
            errorMessage = message;
            unexpectedException = true;
            throw new CommandFailure(e);
        } catch (TooManyRequestsException e) {
            String message = "Executing too many Spotify requests at the moment, please try again later.";
            messageService.sendException(message,
                command.getContext().getChannel());
            logger.warn("Executing too many Spotify requests", e);
            errorMessage = message;
            unexpectedException = true;
            throw new CommandFailure(e);
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
            throw new CommandFailure(e);
        } catch (ErrorResponseException e) {
            messageService.sendException(String.format("Discord returned error (code: %s): %s", e.getErrorCode(), e.getMeaning()), command.getContext().getChannel());
            logger.error("Blocking Discord request returned error", e);
            throw new CommandFailure(e);
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
            postCommand(command, completedSuccessfully, failedManually, errorMessage, unexpectedException);
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
        COMMAND_HISTORY_SERVICE.execute(() -> {
            CommandHistory history = context.getCommandHistory();
            if (history != null) {
                history.setDurationMs(System.currentTimeMillis() - history.getStartMillis());
                history.setCompletedSuccessfully(completedSuccessfully);
                history.setFailedManually(failedManually);
                history.setUnexpectedException(unexpectedException);
                history.setErrorMessage(errorMessage);

                StaticSessionProvider.consumeSession(session -> session.persist(history));
            } else {
                logger.warn("Command " + command + " has no history");
            }
        });

    }

}
