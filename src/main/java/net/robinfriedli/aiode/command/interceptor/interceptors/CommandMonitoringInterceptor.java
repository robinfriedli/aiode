package net.robinfriedli.aiode.command.interceptor.interceptors;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.aiode.command.interceptor.CommandInterceptor;
import net.robinfriedli.aiode.concurrent.CommandExecutionTask;
import net.robinfriedli.aiode.concurrent.DaemonThreadPool;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.aiode.function.LoggingRunnable;

/**
 * Interceptor that monitors a command execution and sends a "Still loading..." message if the command takes longer than
 * 5 seconds to signal to the user that the bot is still execution the command
 */
public class CommandMonitoringInterceptor extends AbstractChainableCommandInterceptor {

    private static final int MESSAGE_AFTER_THRESHOLD = 5000;
    private static final int LOGGER_WARNING_AFTER_THRESHOLD = 115000;

    private final MessageService messageService;
    private final Logger logger;

    public CommandMonitoringInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next, MessageService messageService) {
        super(contribution, next);
        this.messageService = messageService;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void performChained(Command command) {
        CommandContext context = command.getContext();

        CommandExecutionTask task = command.getTask();
        if (task == null) {
            return;
        }

        Future<?> monitoring = DaemonThreadPool.submit((LoggingRunnable) () -> {
            Thread thread = Thread.currentThread();
            String oldName = thread.getName();
            thread.setName("command-monitoring-" + context);
            boolean slashCommand = context.isSlashCommand();

            CompletableFuture<Message> stillLoadingMessage = null;
            CompletableFuture<Message> warningMessage = null;
            try {
                task.await(MESSAGE_AFTER_THRESHOLD);

                if (!task.isDone()) {
                    if (!slashCommand) {
                        EmbedBuilder embedBuilder = new EmbedBuilder();
                        embedBuilder.setDescription("Still loading...");
                        stillLoadingMessage = messageService.send(embedBuilder, context.getChannel());
                    }

                    task.await(LOGGER_WARNING_AFTER_THRESHOLD);
                    if (!task.isDone()) {
                        if (!slashCommand) {
                            EmbedBuilder warningEmbed = new EmbedBuilder();
                            warningEmbed.setColor(Color.RED);
                            warningEmbed.setTitle("Command timeout");
                            warningEmbed.setDescription(
                                String.format(
                                    "Your command '%s' is taking very long to execute. " +
                                        "If the command is not responding, consider interrupting it using the abort command.",
                                    command.display())
                            );
                            warningMessage = messageService.send(warningEmbed.build(), context.getChannel());
                        }

                        logger.warn(String.format("Command [%s] on guild %s has exceeded the warn limit for execution duration of %d millis.",
                            command.display(), context.getGuild(), MESSAGE_AFTER_THRESHOLD + LOGGER_WARNING_AFTER_THRESHOLD));
                    }

                    task.await();
                    deleteMessages(stillLoadingMessage, warningMessage);
                }
            } catch (InterruptedException e) {
                // CommandExecutionInterceptor interrupts monitoring in post command
                deleteMessages(stillLoadingMessage, warningMessage);
            } finally {
                thread.setName(oldName);
            }
        });
        context.registerMonitoring(monitoring);
    }

    private void deleteMessages(CompletableFuture<Message> stillLoadingMessage, CompletableFuture<Message> warningMessage) {
        if (stillLoadingMessage != null) {
            stillLoadingMessage.thenAccept(message -> {
                try {
                    message.delete().queue();
                } catch (Exception ignored) {
                }
            });
        }

        if (warningMessage != null) {
            warningMessage.thenAccept(message -> {
                try {
                    message.delete().queue();
                } catch (Exception ignored) {
                }
            });
        }
    }

}
