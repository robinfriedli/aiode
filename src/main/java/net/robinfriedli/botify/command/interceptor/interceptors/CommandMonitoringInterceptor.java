package net.robinfriedli.botify.command.interceptor.interceptors;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;

public class CommandMonitoringInterceptor extends AbstractChainableCommandInterceptor {

    private final MessageService messageService;
    private final Logger logger;

    public CommandMonitoringInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next, MessageService messageService) {
        super(contribution, next);
        this.messageService = messageService;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void performChained(AbstractCommand command) {
        CommandContext context = command.getContext();
        Thread monitoringThread = new Thread(() -> {
            CommandExecutionThread commandExecutionThread = command.getThread();
            try {
                commandExecutionThread.join(5000);
            } catch (InterruptedException e) {
                return;
            }
            if (commandExecutionThread.isAlive()) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setDescription("Still loading...");
                CompletableFuture<Message> futureMessage = messageService.send(embedBuilder, context.getChannel());

                try {
                    commandExecutionThread.join();
                } catch (InterruptedException ignored) {
                    // CommandExecutionInterceptor interrupts monitoring in post command
                    futureMessage.thenAccept(message -> {
                        try {
                            message.delete().queue();
                        } catch (Exception e) {
                            OffsetDateTime timeCreated = message.getTimeCreated();
                            ZonedDateTime zonedDateTime = timeCreated.atZoneSameInstant(ZoneId.systemDefault());
                            logger.warn(String.format("Could not delete still loading message from: %s channel: %s guild: %s",
                                zonedDateTime.toString(), context.getChannel(), context.getGuild()));
                        }
                    });
                }
            }
        });
        monitoringThread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
        context.registerMonitoring(monitoringThread);
        context.startMonitoring();
    }
}
