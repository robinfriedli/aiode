package net.robinfriedli.botify.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.command.commands.AnswerCommand;
import net.robinfriedli.botify.discord.DiscordListener;
import net.robinfriedli.botify.discord.GuildSpecificationManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Main hub for commands containing all available commands. Is responsible for instantiating an {@link AbstractCommand}
 * based on the given {@link CommandContext} as entered by the user and then passes it to the {@link CommandExecutor} for
 * execution. Also holds all unanswered {@link ClientQuestionEvent} and some global fields used in commands.
 */
public class CommandManager {

    private final CommandExecutor commandExecutor;
    private final DiscordListener discordListener;
    private final LoginManager loginManager;
    private final Context commandContributionContext;
    private final Context commandInterceptorContext;
    private final Logger logger;

    /**
     * all unanswered Questions. Questions get removed after 5 minutes or after the same user enters a different command.
     */
    private List<ClientQuestionEvent> pendingQuestions;

    public CommandManager(CommandExecutor commandExecutor,
                          DiscordListener discordListener,
                          LoginManager loginManager,
                          Context commandContributionContext,
                          Context commandInterceptorContext, Logger logger) {
        this.commandExecutor = commandExecutor;
        this.discordListener = discordListener;
        this.loginManager = loginManager;
        this.commandContributionContext = commandContributionContext;
        this.commandInterceptorContext = commandInterceptorContext;
        this.logger = logger;
        pendingQuestions = Lists.newArrayList();
    }

    public void runCommand(CommandContext context) {
        String command = context.getCommandBody();

        if (command.isBlank()) {
            return;
        }

        CommandContribution commandContribution = (CommandContribution) commandContributionContext.query(and(
            xmlElement -> command.toLowerCase().startsWith(xmlElement.getAttribute("identifier").getValue()),
            instanceOf(CommandContribution.class)
        )).getOnlyResult();

        if (commandContribution == null) {
            throw new InvalidCommandException("Unknown command");
        }

        String selectedCommand = commandContribution.getAttribute("identifier").getValue();
        String commandInput = command.substring(selectedCommand.length()).trim();
        AbstractCommand commandInstance = commandContribution.instantiate(this, context, commandInput);

        if (!(commandInstance instanceof AnswerCommand)) {
            // if the user has a pending question, destroy
            getQuestion(context).ifPresent(ClientQuestionEvent::destroy);
        }

        commandInterceptorContext.getInstancesOf(CommandInterceptorContribution.class)
            .stream()
            .sorted(Comparator.comparingInt(o -> o.getAttribute("order").getInt()))
            .forEach(commandInterceptorContribution -> {
                try {
                    CommandInterceptor interceptor = commandInterceptorContribution.instantiate();
                    interceptor.intercept(commandInstance);
                } catch (InvalidCommandException | ForbiddenCommandException e) {
                    throw e;
                } catch (Throwable e) {
                    logger.error("Exception in interceptor", e);
                }
            });

        commandExecutor.runCommand(commandInstance);
    }

    public Optional<AbstractCommand> getCommand(CommandContext commandContext, String name) {
        CommandContribution commandContribution = getCommandContribution(name);

        if (commandContribution == null) {
            return Optional.empty();
        }

        return Optional.of(commandContribution.instantiate(this, commandContext, ""));
    }

    public List<AbstractCommand> getAllCommands(CommandContext commandContext) {
        List<AbstractCommand> commands = Lists.newArrayList();
        for (CommandContribution commandContribution : commandContributionContext.getInstancesOf(CommandContribution.class)) {
            commands.add(commandContribution.instantiate(this, commandContext, ""));
        }

        return commands;
    }

    public CommandContribution getCommandContribution(String name) {
        return (CommandContribution) commandContributionContext.query(and(
            attribute("identifier").is(name),
            instanceOf(CommandContribution.class)
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

    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public GuildSpecificationManager getGuildManager() {
        return discordListener.getGuildSpecificationManager();
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
