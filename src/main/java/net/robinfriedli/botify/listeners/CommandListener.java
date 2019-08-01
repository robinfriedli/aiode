package net.robinfriedli.botify.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.CommandExceptionHandler;
import net.robinfriedli.botify.exceptions.UserException;
import org.hibernate.SessionFactory;

/**
 * Listener responsible for filtering entered commands and creating a {@link CommandContext} to pass to the
 * {@link CommandManager}
 */
public class CommandListener extends ListenerAdapter {

    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final GuildManager guildManager;
    private final Logger logger;
    private final MessageService messageService;
    private final SessionFactory sessionFactory;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public CommandListener(CommandExecutionQueueManager executionQueueManager,
                           CommandManager commandManager,
                           GuildManager guildManager,
                           SessionFactory sessionFactory,
                           SpotifyApi.Builder spotifyApiBuilder) {
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.guildManager = guildManager;
        this.messageService = new MessageService();
        this.sessionFactory = sessionFactory;
        this.spotifyApiBuilder = spotifyApiBuilder;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            Guild guild = event.getGuild();
            Message message = event.getMessage();
            String msg = message.getContentDisplay();
            String botName = guildManager.getNameForGuild(guild);
            String prefix = guildManager.getPrefixForGuild(guild);

            String lowerCaseMsg = msg.toLowerCase();
            boolean startsWithPrefix = !Strings.isNullOrEmpty(prefix) && lowerCaseMsg.startsWith(prefix.toLowerCase());
            boolean startsWithName = !Strings.isNullOrEmpty(botName) && lowerCaseMsg.startsWith(botName.toLowerCase());
            if (startsWithPrefix || startsWithName || lowerCaseMsg.startsWith("$botify")) {
                String usedPrefix = extractUsedPrefix(message, lowerCaseMsg, botName, prefix, startsWithName, startsWithPrefix);
                startCommandExecution(usedPrefix, message, guild);
            }
        }
    }

    private String extractUsedPrefix(Message message, String lowerCaseMsg, String botName, String prefix, boolean startsWithName, boolean startsWithPrefix) {
        // specify with which part of the input string the bot was referenced, this helps trimming the command later
        String namePrefix;
        if (lowerCaseMsg.startsWith("$botify")) {
            namePrefix = "$botify";
        } else {
            if (startsWithName && startsWithPrefix) {
                if (prefix.equals(botName) || prefix.length() > botName.length()) {
                    namePrefix = prefix;
                } else {
                    namePrefix = botName;
                }
            } else if (startsWithName) {
                namePrefix = botName;
            } else {
                namePrefix = prefix;
            }
        }

        MessageService messageService = new MessageService();
        if (namePrefix == null) {
            // realistically should never happen but catch this edge case just to be sure
            messageService.sendException("Something went wrong parsing your command, try starting with \"$botify\" instead.", message.getChannel());
            logger.error("Name prefix null for input " + message.getContentDisplay() + ". Bot name: " + botName + "; Prefix: " + prefix);
        }

        return namePrefix;
    }

    private void startCommandExecution(String namePrefix, Message message, Guild guild) {
        ThreadExecutionQueue queue = executionQueueManager.getForGuild(guild);
        String commandBody = message.getContentDisplay().substring(namePrefix.length()).trim();
        GuildContext guildContext = guildManager.getContextForGuild(guild);
        CommandContext commandContext = new CommandContext(commandBody, message, sessionFactory, spotifyApiBuilder.build(), guildContext);
        CommandExecutionThread commandExecutionThread = new CommandExecutionThread(commandContext, queue, () -> {
            try {
                commandManager.runCommand(commandContext);
            } catch (UserException e) {
                messageService.sendError(e.getMessage(), message.getChannel());
            } finally {
                commandContext.closeSession();
            }
        });

        commandContext.registerMonitoring(new Thread(() -> {
            try {
                commandExecutionThread.join(5000);
            } catch (InterruptedException e) {
                return;
            }
            if (commandExecutionThread.isAlive()) {
                messageService.send("Still loading...", message.getChannel());
            }
        }));

        commandExecutionThread.setUncaughtExceptionHandler(new CommandExceptionHandler(commandContext, logger));
        commandExecutionThread.setName("botify command execution: " + commandContext);
        boolean queued = !queue.add(commandExecutionThread);

        if (queued) {
            messageService.sendError("Executing too many commands concurrently. This command will be executed after one has finished.", message.getChannel());
        }

    }

}
