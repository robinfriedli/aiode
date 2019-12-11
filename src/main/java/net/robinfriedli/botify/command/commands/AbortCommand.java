package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.exec.PooledTrackLoadingExecutor;
import net.robinfriedli.botify.audio.exec.ReplaceableTrackLoadingExecutor;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;

public class AbortCommand extends AbstractCommand {

    public AbortCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, String identifier, String description) {
        super(commandContribution, context, commandManager, commandBody, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        Thread commandExecutionThread = Thread.currentThread();
        Thread abortThread = new Thread(() -> {
            try {
                commandExecutionThread.join();
            } catch (InterruptedException ignored) {
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
        abortThread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
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
