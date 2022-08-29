package net.robinfriedli.aiode.discord.listeners;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.argument.ArgumentController;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.concurrent.EventHandlerPool;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.concurrent.ThreadExecutionQueue;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.discord.property.AbstractGuildProperty;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.discord.property.properties.BotNameProperty;
import net.robinfriedli.aiode.discord.property.properties.PrefixProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.exceptions.UserException;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Listener responsible for filtering entered commands and creating a {@link CommandContext} to pass to the
 * {@link CommandManager}
 */
@Component
public class CommandListener extends ListenerAdapter {

    private final Logger logger = LoggerFactory.getLogger(getClass());

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
            boolean startsWithLegacyPrefix = lowerCaseMsg.startsWith("$botify");

            if (startsWithPrefix
                || startsWithName
                || startsWithDefaultPrefix
                || startsWithDefaultName
                || startsWithLegacyPrefix
            ) {
                String usedPrefix = extractUsedPrefix(
                    botName,
                    prefix,
                    startsWithName,
                    startsWithPrefix,
                    startsWithDefaultName,
                    startsWithDefaultPrefix,
                    startsWithLegacyPrefix
                );
                startCommandExecution(usedPrefix, message, guild, guildContext, session, event);
            }
        }));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        EventHandlerPool.execute(() -> hibernateComponent.consumeSession(session -> {
            event.deferReply(false).queue();
            Guild guild = event.getGuild();
            GuildContext guildContext = guildManager.getContextForGuild(guild);

            String commandBody;
            String commandString = event.getCommandString();
            if (commandString.length() > 1) {
                commandBody = commandString.substring(1);
            } else {
                commandBody = commandString;
            }

            CommandContext commandContext = new CommandContext(event, guildContext, hibernateComponent.getSessionFactory(), spotifyApiBuilder, commandBody);
            ExecutionContext.Current.set(commandContext.fork());
            ThreadExecutionQueue queue = executionQueueManager.getForGuild(guild);
            try {
                AbstractCommand commandInstance = commandManager
                    .instantiateCommandForIdentifier(event.getName(), commandContext, session)
                    .orElseThrow(() -> {
                        logger.error("No command found for slash command identifier {} on guild {}", event.getName(), guild);
                        return new UserException("No such command: " + event.getName());
                    });

                ArgumentController argumentController = commandInstance.getArgumentController();
                for (OptionMapping option : event.getOptions()) {
                    if (option.getType() == OptionType.BOOLEAN && !option.getAsBoolean()) {
                        continue;
                    }
                    String optionName = option.getName();
                    String optionValue = option.getAsString();
                    argumentController.setArgument(optionName, optionValue);
                    if ("input".equals(optionName)) {
                        commandInstance.setCommandInput(optionValue);
                    }
                }

                commandManager.runCommand(commandInstance, queue);
            } catch (UserException e) {
                EmbedBuilder embedBuilder = e.buildEmbed();
                messageService.sendTemporary(embedBuilder.build(), commandContext.getChannel());
                event.getInteraction().getHook().deleteOriginal().queue();
            }
        }));
    }

    private String extractUsedPrefix(
        String botName,
        String prefix,
        boolean startsWithName,
        boolean startsWithPrefix,
        boolean startsWithDefaultName,
        boolean startsWithDefaultPrefix,
        boolean startsWithLegacyPrefix
    ) {
        boolean[] matches = {startsWithName, startsWithPrefix, startsWithDefaultName, startsWithDefaultPrefix, startsWithLegacyPrefix};
        String[] strings = {botName, prefix, defaultBotName, defaultPrefix, "$botify"};
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
        CommandContext commandContext = new CommandContext(event, guildContext, hibernateComponent.getSessionFactory(), spotifyApiBuilder, commandBody);
        ExecutionContext.Current.set(commandContext.fork());
        try {
            Optional<AbstractCommand> commandInstance = commandManager.instantiateCommandForContext(commandContext, session);
            commandInstance.ifPresent(command -> commandManager.runCommand(command, queue));
        } catch (UserException e) {
            EmbedBuilder embedBuilder = e.buildEmbed();
            messageService.sendTemporary(embedBuilder.build(), commandContext.getChannel());
        }
    }

}
