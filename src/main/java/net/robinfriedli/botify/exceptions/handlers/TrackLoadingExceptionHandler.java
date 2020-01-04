package net.robinfriedli.botify.exceptions.handlers;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class TrackLoadingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Logger logger;
    private final MessageChannel channel;
    @Nullable
    private final CommandContext commandContext;

    public TrackLoadingExceptionHandler(Logger logger, MessageChannel channel, @Nullable CommandContext commandContext) {
        this.logger = logger;
        this.channel = channel;
        this.commandContext = commandContext;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String commandContextSuffix = commandContext != null ? " (started by command: " + commandContext.getId() + ")" : "";
        logger.error("Exception while loading tracks" + commandContextSuffix, e);
        if (channel != null) {
            EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e);
            embedBuilder.setDescription("There has been an error while loading some tracks. Please try again.");

            if (commandContext != null) {
                embedBuilder.addField("CommandContext ID", commandContext.getId(), false);
            }

            MessageService messageService = Botify.get().getMessageService();
            messageService.send(embedBuilder.build(), channel);
        }
    }

}
