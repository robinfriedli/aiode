package net.robinfriedli.botify.discord.listeners;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.botify.concurrent.EventHandlerPool;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.discord.property.properties.BotNameProperty;
import net.robinfriedli.botify.discord.property.properties.PrefixProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.exceptions.UserException;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Listener responsible for filtering entered commands and creating a {@link CommandContext} to pass to the
 * {@link CommandManager}
 */
@Component
public class CommandListener extends ListenerAdapter {

    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final MessageService messageService;
    private final SpotifyApi.Builder spotifyApiBuilder;

    private final String defaultBotName;
    private final String defaultPrefix;

    public CommandListener(CommandExecutionQueueManager executionQueueManager,
                           CommandManager commandManager,
                           GuildManager guildManager,
                           GuildPropertyManager guildPropertyManager,
                           HibernateComponent hibernateComponent,
                           MessageService messageService,
                           SpotifyApi.Builder spotifyApiBuilder) {
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.messageService = messageService;
        this.spotifyApiBuilder = spotifyApiBuilder;

        defaultBotName = guildPropertyManager
            .getPropertyOptional("botName")
            .map(AbstractGuildProperty::getDefaultValue)
            .orElse(BotNameProperty.DEFAULT_FALLBACK)
            .toLowerCase();
        defaultPrefix = guildPropertyManager
            .getPropertyOptional("prefix")
            .map(AbstractGuildProperty::getDefaultValue)
            .orElse(PrefixProperty.DEFAULT_FALLBACK)
            .toLowerCase();
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        EventHandlerPool.execute(() -> hibernateComponent.consumeSession(session -> {
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
            boolean startsWithDefaultPrefix = !Strings.isNullOrEmpty(defaultPrefix) && lowerCaseMsg.startsWith(defaultPrefix);
            boolean startsWithDefaultName = !Strings.isNullOrEmpty(defaultBotName) && lowerCaseMsg.startsWith(defaultBotName);

            if (startsWithPrefix
                || startsWithName
                || startsWithDefaultPrefix
                || startsWithDefaultName
            ) {
                String usedPrefix = extractUsedPrefix(
                    botName,
                    prefix,
                    startsWithName,
                    startsWithPrefix,
                    startsWithDefaultName,
                    startsWithDefaultPrefix
                );
                startCommandExecution(usedPrefix, message, guild, guildContext, session, event);
            }
        }));
    }

    private String extractUsedPrefix(
        String botName,
        String prefix,
        boolean startsWithName,
        boolean startsWithPrefix,
        boolean startsWithDefaultName,
        boolean startsWithDefaultPrefix
    ) {
        boolean[] matches = {startsWithName, startsWithPrefix, startsWithDefaultName, startsWithDefaultPrefix};
        String[] strings = {botName, prefix, defaultBotName, defaultPrefix};
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
        CommandContext commandContext = new CommandContext(event, guildContext, hibernateComponent.getSessionFactory(), spotifyApiBuilder.build(), commandBody);
        ExecutionContext.Current.set(commandContext.threadSafe());
        try {
            Optional<AbstractCommand> commandInstance = commandManager.instantiateCommandForContext(commandContext, session);
            commandInstance.ifPresent(command -> commandManager.runCommand(command, queue));
        } catch (UserException e) {
            EmbedBuilder embedBuilder = e.buildEmbed();
            messageService.sendTemporary(embedBuilder.build(), commandContext.getChannel());
        }
    }

}
