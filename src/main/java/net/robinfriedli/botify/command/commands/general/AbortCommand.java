package net.robinfriedli.botify.command.commands.general;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.exec.PooledTrackLoadingExecutor;
import net.robinfriedli.botify.audio.exec.ReplaceableTrackLoadingExecutor;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.botify.concurrent.CommandExecutionTask;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;

public class AbortCommand extends AbstractCommand {

    public AbortCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        CommandExecutionTask commandExecutionTask = getTask();

        Thread abortThread = new Thread(() -> {
            if (commandExecutionTask != null) {
                try {
                    commandExecutionTask.await();
                } catch (InterruptedException ignored) {
                }
            }

            CommandExecutionQueueManager executionQueueManager = Botify.get().getExecutionQueueManager();
            GuildContext guildContext = getContext().getGuildContext();
            ThreadExecutionQueue executionQueue = executionQueueManager.getForGuild(getContext().getGuild());
            PooledTrackLoadingExecutor pooledTrackLoadingExecutor = guildContext.getPooledTrackLoadingExecutor();
            ReplaceableTrackLoadingExecutor replaceableTrackLoadingExecutor = guildContext.getReplaceableTrackLoadingExecutor();
            if (executionQueue.isIdle() && pooledTrackLoadingExecutor.isIdle() && replaceableTrackLoadingExecutor.isIdle()) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setDescription("No commands are currently running");
                getMessageService().sendTemporary(embedBuilder, getContext().getChannel());
                setFailed(true);
            } else {
                executionQueue.abortAll();
                pooledTrackLoadingExecutor.abortAll();
                replaceableTrackLoadingExecutor.abort();
                sendSuccess("Sent all currently running commands an interrupt signal and cancelled queued commands.");
            }
        });
        abortThread.setName("botify abort thread");
        abortThread.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler());
        abortThread.start();
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }
}
