package net.robinfriedli.botify.exceptions;

import java.awt.Color;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.MessageService;
import org.codehaus.groovy.control.CompilationFailedException;

public class ExceptionUtils {

    public static Throwable getRootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        return root;
    }

    public static EmbedBuilder buildErrorEmbed(Throwable e) {
        Throwable exception = e instanceof CommandRuntimeException ? e.getCause() : e;
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.RED);
        appendException(embedBuilder, exception, false, 0);
        return embedBuilder;
    }

    public static void handleCommandException(Throwable e, Command command, Logger logger) {
        if (e instanceof CommandFailure) {
            return;
        }

        CommandContext commandContext = command.getContext();

        if (Botify.isShuttingDown()) {
            logger.warn(String.format("Suppressed error from command %s because it happened during shutdown: %s", commandContext.getId(), e));
            return;
        }

        MessageChannel channel = commandContext.getChannel();
        String commandDisplay = command.display();
        MessageService messageService = Botify.get().getMessageService();

        if (e instanceof UserException) {
            EmbedBuilder embedBuilder = ((UserException) e).buildEmbed();
            messageService.sendTemporary(embedBuilder.build(), channel);
        } else {
            EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e);
            embedBuilder.addField("CommandContext ID", commandContext.getId(), false);
            messageService.send(embedBuilder.build(), channel);
            logger.error(String.format("Exception while handling command %s on guild %s", commandDisplay, commandContext.getGuild().getName()), e);
        }
    }

    public static void handleTrackLoadingException(Throwable e, Logger logger, @Nullable ExecutionContext executionContext, @Nullable MessageChannel messageChannel) {
        if (e instanceof InterruptedException || ExceptionUtils.getRootCause(e) instanceof InterruptedException) {
            logger.warn("Suppressed InterruptedException " + e + " while loading tracks.");
            return;
        }

        String commandContextSuffix = executionContext != null ? " (started by command: " + executionContext.getId() + ")" : "";
        String msg = "Exception while loading tracks" + commandContextSuffix;

        if (Botify.isShuttingDown()) {
            logger.warn(String.format("Suppressed error because it happened during shutdown: %s: %s", msg, e));
            return;
        }

        logger.error(msg, e);
        if (messageChannel != null) {
            EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e);
            embedBuilder.setDescription("There has been an error while loading some tracks. Please try again.");

            if (executionContext != null) {
                embedBuilder.addField("CommandContext ID", executionContext.getId(), false);
            }

            MessageService messageService = Botify.get().getMessageService();
            messageService.send(embedBuilder.build(), messageChannel);
        }
    }

    private static void appendException(EmbedBuilder embedBuilder, Throwable e, boolean isCause, int counter) {
        if (counter >= 5) {
            return;
        }

        String message;
        if (e instanceof GoogleJsonResponseException) {
            message = ((GoogleJsonResponseException) e).getDetails().getMessage();
        } else {
            message = e.getMessage();
        }

        String value = String.format("%s: %s", e.getClass().getSimpleName(), message);

        if (value.length() > 1000) {
            value = value.substring(0, 995) + "[...]";
        }

        if (e instanceof CompilationFailedException) {
            value = "```" + value + "```";
        }

        embedBuilder.addField(isCause ? "Caused by" : "Exception", value, false);

        if (e.getCause() != null) {
            appendException(embedBuilder, e.getCause(), true, counter + 1);
        }
    }

}
