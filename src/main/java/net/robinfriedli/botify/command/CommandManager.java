package net.robinfriedli.botify.command;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.command.commands.AddCommand;
import net.robinfriedli.botify.command.commands.AnswerCommand;
import net.robinfriedli.botify.command.commands.CreateCommand;
import net.robinfriedli.botify.command.commands.DeleteCommand;
import net.robinfriedli.botify.command.commands.ExportCommand;
import net.robinfriedli.botify.command.commands.HelpCommand;
import net.robinfriedli.botify.command.commands.ListCommand;
import net.robinfriedli.botify.command.commands.LoginCommand;
import net.robinfriedli.botify.command.commands.PauseCommand;
import net.robinfriedli.botify.command.commands.PlayCommand;
import net.robinfriedli.botify.command.commands.QueueCommand;
import net.robinfriedli.botify.command.commands.RemoveCommand;
import net.robinfriedli.botify.command.commands.RenameCommand;
import net.robinfriedli.botify.command.commands.RewindCommand;
import net.robinfriedli.botify.command.commands.SearchCommand;
import net.robinfriedli.botify.command.commands.SkipCommand;
import net.robinfriedli.botify.command.commands.StopCommand;
import net.robinfriedli.botify.discord.DiscordListener;
import net.robinfriedli.botify.discord.GuildSpecificationManager;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;

/**
 * Main hub for commands containing all available commands. Is responsible for instantiating an {@link AbstractCommand}
 * based on the given {@link CommandContext} as entered by the user and then passes it to the {@link CommandExecutor} for
 * execution. Also holds all unanswered {@link ClientQuestionEvent} and some global fields used in commands.
 */
public class CommandManager {

    private final static Map<String, Class<? extends AbstractCommand>> COMMANDS = new CaseInsensitiveMap<>();

    static {
        COMMANDS.put("play", PlayCommand.class);
        COMMANDS.put("rename", RenameCommand.class);
        COMMANDS.put("login", LoginCommand.class);
        COMMANDS.put("search", SearchCommand.class);
        COMMANDS.put("queue", QueueCommand.class);
        COMMANDS.put("export", ExportCommand.class);
        COMMANDS.put("answer", AnswerCommand.class);
        COMMANDS.put("pause", PauseCommand.class);
        COMMANDS.put("stop", StopCommand.class);
        COMMANDS.put("skip", SkipCommand.class);
        COMMANDS.put("rewind", RewindCommand.class);
        COMMANDS.put("list", ListCommand.class);
        COMMANDS.put("add", AddCommand.class);
        COMMANDS.put("remove", RemoveCommand.class);
        COMMANDS.put("create", CreateCommand.class);
        COMMANDS.put("delete", DeleteCommand.class);
        COMMANDS.put("help", HelpCommand.class);
    }

    private final CommandExecutor commandExecutor;
    private final DiscordListener discordListener;
    private final LoginManager loginManager;
    private final SpotifyApi spotifyApi;

    /**
     * all unanswered Questions. Questions get removed after 5 minutes or after the same user enters a different command.
     */
    private List<ClientQuestionEvent> pendingQuestions;

    public CommandManager(CommandExecutor commandExecutor, DiscordListener discordListener, LoginManager loginManager, SpotifyApi spotifyApi) {
        this.commandExecutor = commandExecutor;
        this.discordListener = discordListener;
        this.loginManager = loginManager;
        this.spotifyApi = spotifyApi;
        pendingQuestions = Lists.newArrayList();
    }

    public void runCommand(CommandContext context) {
        String command = context.getCommandBody();

        if (command.isBlank()) {
            return;
        }

        COMMANDS
            .keySet()
            .stream()
            .filter(s -> command.toLowerCase().startsWith(s))
            .findFirst()
            .ifPresentOrElse(selectedCommand -> {
                String commandInput = command.substring(selectedCommand.length()).trim();
                Class<? extends AbstractCommand> commandClass = COMMANDS.get(selectedCommand);

                try {
                    Constructor<? extends AbstractCommand> constructor =
                        commandClass.getConstructor(CommandContext.class, CommandManager.class, String.class);
                    AbstractCommand commandInstance = constructor.newInstance(context, this, commandInput);

                    if (!(commandInstance instanceof AnswerCommand)) {
                        // if the user has a pending question, destroy
                        getQuestion(context).ifPresent(ClientQuestionEvent::destroy);
                    }

                    commandExecutor.runCommand(commandInstance);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof InvalidCommandException) {
                        throw (InvalidCommandException) cause;
                    }
                    throw new RuntimeException("Exception while invoking constructor of " + commandClass.getSimpleName(), e);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("Class " + commandClass.getSimpleName() +
                        " does not have a constructor matching CommandContext, CommandManager, String", e);
                } catch (InstantiationException e) {
                    throw new RuntimeException("Command class " + commandClass.getSimpleName() + " could not be instantiated.", e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Cannot access constructor of " + commandClass.getSimpleName(), e);
                }
            }, () -> {
                throw new InvalidCommandException("Unknown command");
            });
    }

    public Optional<AbstractCommand> getCommand(CommandContext commandContext, String name) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class<? extends AbstractCommand> commandClass = COMMANDS.get(name);
        if (commandClass != null) {
            Constructor<? extends AbstractCommand> constructor = commandClass.getConstructor(CommandContext.class, CommandManager.class, String.class);
            return Optional.of(constructor.newInstance(commandContext, this, ""));
        } else {
            return Optional.empty();
        }
    }

    public List<AbstractCommand> getAllCommands(CommandContext commandContext) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        List<AbstractCommand> commands = Lists.newArrayList();
        for (Class<? extends AbstractCommand> commandClass : COMMANDS.values()) {
            Constructor<? extends AbstractCommand> constructor = commandClass.getConstructor(CommandContext.class, CommandManager.class, String.class);
            commands.add(constructor.newInstance(commandContext, this, ""));
        }

        return commands;
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

    public GuildSpecificationManager getNameManager() {
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

    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }

}
