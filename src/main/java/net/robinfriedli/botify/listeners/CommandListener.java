package net.robinfriedli.botify.listeners;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
import org.hibernate.SessionFactory;

/**
 * Listener responsible for filtering entered commands and creating a {@link CommandContext} to pass to the
 * {@link CommandManager}
 */
public class CommandListener extends ListenerAdapter {

    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final ExecutorService commandConceptionPool;
    private final GuildManager guildManager;
    private final MessageService messageService;
    private final SessionFactory sessionFactory;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public CommandListener(CommandExecutionQueueManager executionQueueManager,
                           CommandManager commandManager,
                           GuildManager guildManager,
                           MessageService messageService,
                           SessionFactory sessionFactory,
                           SpotifyApi.Builder spotifyApiBuilder) {
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.commandConceptionPool = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return thread;
        });
        this.guildManager = guildManager;
        this.messageService = messageService;
        this.sessionFactory = sessionFactory;
        this.spotifyApiBuilder = spotifyApiBuilder;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            commandConceptionPool.execute(() -> {
                try (Session session = sessionFactory.openSession()) {
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
                    boolean startsWithFallback = lowerCaseMsg.startsWith("$botify");
                    if (startsWithPrefix || startsWithName || startsWithFallback) {
                        String usedPrefix = extractUsedPrefix(botName, prefix, startsWithName, startsWithPrefix, startsWithFallback);
                        startCommandExecution(usedPrefix, message, guild, guildContext, session, event);
                    }
                }
            });
        }
    }

    private String extractUsedPrefix(String botName, String prefix, boolean startsWithName, boolean startsWithPrefix, boolean startsWithFallback) {
        boolean[] matches = {startsWithName, startsWithPrefix, startsWithFallback};
        String[] strings = {botName, prefix, "$botify"};

        return getLongestMatch(matches, strings);
    }

    private String getLongestMatch(boolean[] matches, String[] strings) {
        if (matches.length != strings.length) {
            throw new IllegalArgumentException("Size of match array does not match string array");
        }

        String match = null;

        for (int i = 0; i < strings.length; i++) {
            String s = strings[i];
            if (matches[i] && (match == null || match.length() < s.length())) {
                match = s;
            }
        }

        return Objects.requireNonNull(match);
    }

    private void startCommandExecution(String namePrefix, Message message, Guild guild, GuildContext guildContext, Session session, GuildMessageReceivedEvent event) {
        ThreadExecutionQueue queue = executionQueueManager.getForGuild(guild);
        String commandBody = message.getContentDisplay().substring(namePrefix.length()).trim();
        CommandContext commandContext = new CommandContext(event, guildContext, sessionFactory, spotifyApiBuilder.build(), commandBody);
        try {
            Optional<AbstractCommand> commandInstance = commandManager.instantiateCommandForContext(commandContext, session);
            commandInstance.ifPresent(command -> commandManager.runCommand(command, queue));
        } catch (UserException e) {
            EmbedBuilder embedBuilder = e.buildEmbed();
            messageService.sendTemporary(embedBuilder.build(), commandContext.getChannel());
        }
    }

}
