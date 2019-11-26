package net.robinfriedli.botify.listeners;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.boot.Shutdownable;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Listener responsible for filtering entered commands and creating a {@link CommandContext} to pass to the
 * {@link CommandManager}
 */
@Component
public class CommandListener extends ListenerAdapter implements Shutdownable {

    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final ExecutorService commandConceptionPool;
    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final Logger logger;
    private final MessageService messageService;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public CommandListener(CommandExecutionQueueManager executionQueueManager,
                           CommandManager commandManager,
                           GuildManager guildManager,
                           HibernateComponent hibernateComponent,
                           MessageService messageService,
                           SpotifyApi.Builder spotifyApiBuilder) {
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.commandConceptionPool = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.messageService = messageService;
        this.spotifyApiBuilder = spotifyApiBuilder;
        this.logger = LoggerFactory.getLogger(getClass());
        register();
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            commandConceptionPool.execute(() -> {
                // DO NOT use StaticSessionProvider#invokeWithSession here. Since a CommandContext will be set on this thread
                // and this thread will be reused, StaticSessionProvider#invokeWithSession does not close the session,
                // causing a connection leak filling up the C3P0 connection pool.
                try (Session session = hibernateComponent.getSessionFactory().openSession()) {
                    Guild guild = event.getGuild();
                    Message message = event.getMessage();
                    String msg = message.getContentDisplay();
                    GuildContext guildContext = guildManager.getContextForGuild(guild);
                    GuildSpecification specification = guildContext.getSpecification(session);
                    String botName = specification.getBotName();
                    String prefix = specification.getPrefix();

                    String lowerCaseMsg = msg.toLowerCase();
                    boolean startsWithPrefix = !Strings.isNullOrEmpty(prefix) && lowerCaseMsg.startsWith(prefix.toLowerCase());
                    boolean startsWithName = !Strings.isNullOrEmpty(botName) && lowerCaseMsg.startsWith(botName.toLowerCase());
                    if (startsWithPrefix || startsWithName || lowerCaseMsg.startsWith("$botify")) {
                        String usedPrefix = extractUsedPrefix(message, lowerCaseMsg, botName, prefix, startsWithName, startsWithPrefix);
                        startCommandExecution(usedPrefix, message, guild, guildContext, session, event);
                    }
                }
            });
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

        if (namePrefix == null) {
            // realistically should never happen but catch this edge case just to be sure
            messageService.sendException("Something went wrong parsing your command, try starting with \"$botify\" instead.", message.getChannel());
            logger.error("Name prefix null for input " + message.getContentDisplay() + ". Bot name: " + botName + "; Prefix: " + prefix);
        }

        return namePrefix;
    }

    private void startCommandExecution(String namePrefix, Message message, Guild guild, GuildContext guildContext, Session session, GuildMessageReceivedEvent event) {
        ThreadExecutionQueue queue = executionQueueManager.getForGuild(guild);
        String commandBody = message.getContentDisplay().substring(namePrefix.length()).trim();
        CommandContext commandContext = new CommandContext(event, guildContext, hibernateComponent.getSessionFactory(), spotifyApiBuilder.build(), commandBody);
        CommandContext.Current.set(commandContext);
        try {
            Optional<AbstractCommand> commandInstance = commandManager.instantiateCommandForContext(commandContext, session);
            commandInstance.ifPresent(command -> commandManager.runCommand(command, queue));
        } catch (UserException e) {
            EmbedBuilder embedBuilder = e.buildEmbed();
            messageService.sendTemporary(embedBuilder.build(), commandContext.getChannel());
        }
    }

    @Override
    public void shutdown(int delayMs) {
        commandConceptionPool.shutdown();
    }
}
