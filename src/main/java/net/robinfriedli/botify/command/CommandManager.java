package net.robinfriedli.botify.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.discord.DiscordListener;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.CommandInterceptorContribution;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Main hub for commands containing all available commands. Is responsible for instantiating an {@link AbstractCommand}
 * based on the given {@link CommandContext} as entered by the user and then passes it to the {@link CommandInterceptor}s for
 * execution. Also holds all unanswered {@link ClientQuestionEvent} and some global fields used in commands.
 */
public class CommandManager {

    private final DiscordListener discordListener;
    private final LoginManager loginManager;
    private final Context commandContributionContext;
    private final Context commandInterceptorContext;
    private final Logger logger;
    private final SessionFactory sessionFactory;

    /**
     * all unanswered Questions. Questions get removed after 5 minutes or after the same user enters a different command.
     */
    private List<ClientQuestionEvent> pendingQuestions;

    /**
     * all widgets that currently listen for reactions. Only one widget of the same type per guild may be active.
     */
    private List<AbstractWidget> activeWidgets;

    public CommandManager(DiscordListener discordListener,
                          LoginManager loginManager,
                          Context commandContributionContext,
                          Context commandInterceptorContext,
                          SessionFactory sessionFactory) {
        this.discordListener = discordListener;
        this.loginManager = loginManager;
        this.commandContributionContext = commandContributionContext;
        this.commandInterceptorContext = commandInterceptorContext;
        this.sessionFactory = sessionFactory;
        this.logger = LoggerFactory.getLogger(getClass());
        pendingQuestions = Lists.newArrayList();
        activeWidgets = Lists.newArrayList();
    }

    @SuppressWarnings({"unchecked"})
    public void runCommand(CommandContext context) {
        String command = context.getCommandBody();

        if (command.isBlank()) {
            return;
        }

        CommandContribution commandContribution = getCommandContributionForInput(command);

        AbstractCommand commandInstance;
        if (commandContribution != null) {
            String selectedCommand = commandContribution.getAttribute("identifier").getValue();
            String commandInput = command.substring(selectedCommand.length()).trim();
            commandInstance = commandContribution.instantiate(this, context, commandInput);
        } else {
            try (Session session = sessionFactory.openSession()) {
                // find a preset where the preset name matches the beginning of the command, find the longest matching preset name
                Optional<Preset> optionalPreset = session
                    .createQuery("from " + Preset.class.getName() + " where guild_id = '" + context.getGuild().getId() + "' and lower(name) = substring(lower('" + command + "'), 0, length(name) + 1) order by length(name) desc", Preset.class)
                    .setMaxResults(1)
                    .uniqueResultOptional();
                if (optionalPreset.isPresent()) {
                    Preset preset = optionalPreset.get();
                    commandInstance = preset.instantiateCommand(this, context, command);
                } else {
                    throw new InvalidCommandException("No command or preset found");
                }
            }
        }

        commandInterceptorContext.getInstancesOf(CommandInterceptorContribution.class)
            .stream()
            .sorted(Comparator.comparingInt(o -> o.getAttribute("order").getInt()))
            .forEach(commandInterceptorContribution -> {
                try {
                    CommandInterceptor interceptor = commandInterceptorContribution.instantiate();
                    interceptor.intercept(commandInstance);
                } catch (Throwable e) {
                    boolean interruptCommandExecution = false;
                    try {
                        List<Class<Throwable>> interruptingExceptions = Query
                            .evaluate(xmlElement -> xmlElement.getTagName().equals("interruptingException"))
                            .execute(commandInterceptorContribution.getSubElements())
                            .collect()
                            .stream()
                            .map(xmlElement -> {
                                try {
                                    return (Class<Throwable>) Class.forName(xmlElement.getAttribute("class").getValue());
                                } catch (ClassNotFoundException e1) {
                                    throw new RuntimeException(e1);
                                }
                            })
                            .collect(Collectors.toList());
                        if (interruptingExceptions.stream().anyMatch(clazz -> clazz.isAssignableFrom(e.getClass()))) {
                            interruptCommandExecution = true;
                        } else {
                            logger.error("Unexpected exception in interceptor", e);
                        }
                    } catch (Throwable e2) {
                        logger.error("Exception while handling interceptor exception", e2);
                    }

                    if (interruptCommandExecution) {
                        throw e;
                    }
                }
            });
    }

    public Optional<AbstractCommand> getCommand(CommandContext commandContext, String name) {
        CommandContribution commandContribution = getCommandContribution(name);

        if (commandContribution == null) {
            return Optional.empty();
        }

        return Optional.of(commandContribution.instantiate(this, commandContext, ""));
    }

    public CommandContribution getCommandContributionForInput(String input) {
        return (CommandContribution) commandContributionContext.query(and(
            instanceOf(CommandContribution.class),
            xmlElement -> input.toLowerCase().startsWith(xmlElement.getAttribute("identifier").getValue())
        )).getOnlyResult();
    }

    public List<AbstractCommand> getAllCommands(CommandContext commandContext) {
        List<AbstractCommand> commands = Lists.newArrayList();
        for (CommandContribution commandContribution : commandContributionContext.getInstancesOf(CommandContribution.class)) {
            commands.add(commandContribution.instantiate(this, commandContext, ""));
        }

        return commands;
    }

    public List<CommandContribution> getAllCommandContributions() {
        return commandContributionContext.getInstancesOf(CommandContribution.class);
    }

    public CommandContribution getCommandContribution(String name) {
        return (CommandContribution) commandContributionContext.query(and(
            instanceOf(CommandContribution.class),
            attribute("identifier").is(name)
        )).getOnlyResult();
    }

    public void addQuestion(ClientQuestionEvent question) {
        pendingQuestions.add(question);
    }

    public void removeQuestion(ClientQuestionEvent question) {
        pendingQuestions.remove(question);
    }

    public Optional<ClientQuestionEvent> getQuestion(CommandContext commandContext) {
        return pendingQuestions
            .stream()
            .filter(question -> question.getUser().getId().equals(commandContext.getUser().getId())
                && question.getGuild().getId().equals(commandContext.getGuild().getId()))
            .findFirst();
    }

    public void registerWidget(AbstractWidget widget) {
        List<AbstractWidget> toRemove = Lists.newArrayList();
        try {
            activeWidgets.stream()
                .filter(w -> widget.getMessage().getGuild().getId().equals(w.getMessage().getGuild().getId()))
                .filter(w -> w.getClass().equals(widget.getClass()))
                .forEach(toRemove::add);
        } catch (Throwable e) {
            // JDA weak reference might cause garbage collection issues when getting guild of message
            logger.warn("Exception while removing existing widget", e);
        }
        widget.setup();
        activeWidgets.add(widget);
        toRemove.forEach(AbstractWidget::destroy);
    }

    public Optional<AbstractWidget> getActiveWidget(String messageId) {
        return activeWidgets.stream().filter(widget -> widget.getMessage().getId().equals(messageId)).findAny();
    }

    public void removeWidget(AbstractWidget widget) {
        activeWidgets.remove(widget);
    }

    public GuildManager getGuildManager() {
        return discordListener.getGuildManager();
    }

    public AudioManager getAudioManager() {
        return discordListener.getAudioManager();
    }

    public JxpBackend getJxpBackend() {
        return discordListener.getJxpBackend();
    }

    public DiscordListener getDiscordListener() {
        return discordListener;
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }

    public Logger getLogger() {
        return logger;
    }
}
