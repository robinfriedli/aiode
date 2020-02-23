package net.robinfriedli.botify.exceptions.handlers;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class TrackLoadingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;
    private final MessageChannel channel;
    @Nullable
    private final ExecutionContext executionContext;

    public TrackLoadingExceptionHandler(Logger logger, MessageChannel channel, @Nullable ExecutionContext executionContext) {
        this.logger = logger;
        this.channel = channel;
        this.executionContext = executionContext;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String commandContextSuffix = executionContext != null ? " (started by command: " + executionContext.getId() + ")" : "";
        String msg = "Exception while loading tracks" + commandContextSuffix;

        if (Botify.isShuttingDown()) {
            logger.warn(String.format("Suppressed error because it happened during shutdown: %s: %s", msg, e));
            return;
        }

        logger.error(msg, e);
        if (channel != null) {
            EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e);
            embedBuilder.setDescription("There has been an error while loading some tracks. Please try again.");

            if (executionContext != null) {
                embedBuilder.addField("CommandContext ID", executionContext.getId(), false);
            }

            MessageService messageService = Botify.get().getMessageService();
            messageService.send(embedBuilder.build(), channel);
        }
    }

}
