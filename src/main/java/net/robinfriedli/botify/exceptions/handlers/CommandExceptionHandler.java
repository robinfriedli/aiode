package net.robinfriedli.botify.exceptions.handlers;

import org.slf4j.Logger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class CommandExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final CommandContext commandContext;
    private final Logger logger;

    public CommandExceptionHandler(CommandContext commandContext, Logger logger) {
        this.commandContext = commandContext;
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        MessageChannel channel = commandContext.getChannel();
        String command = commandContext.getMessage().getContentDisplay();
        MessageService messageService = new MessageService();

        EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e);
        embedBuilder.addField("CommandContext ID", commandContext.getId(), false);
        messageService.send(embedBuilder.build(), channel);
        logger.error(String.format("Exception while handling command %s on guild %s", command, commandContext.getGuild().getName()), e);
    }

}
