package net.robinfriedli.aiode.command.commands.general;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.exec.PooledTrackLoadingExecutor;
import net.robinfriedli.aiode.audio.exec.ReplaceableTrackLoadingExecutor;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.concurrent.CommandExecutionTask;
import net.robinfriedli.aiode.concurrent.ThreadExecutionQueue;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;

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

            CommandExecutionQueueManager executionQueueManager = Aiode.get().getExecutionQueueManager();
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
        abortThread.setName("aiode abort thread");
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
