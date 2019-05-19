package net.robinfriedli.botify.command;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import com.google.api.client.util.Sets;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.command.commands.AnswerCommand;
import net.robinfriedli.botify.discord.DiscordListener;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.login.Login;
import net.robinfriedli.botify.util.CheckedRunnable;
import net.robinfriedli.botify.util.Invoker;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

/**
 * Abstract class to extend for each command available to the user. Commands are registered on the {@link CommandManager}
 * and instantiated reflectively based on user input
 */
public abstract class AbstractCommand {

    private final CommandContribution commandContribution;
    private final CommandContext context;
    private final CommandManager commandManager;
    private final ArgumentContribution argumentContribution;
    private final MessageService messageService;
    private final String identifier;
    private final String description;
    private final Category category;
    private boolean requiresInput;
    private String commandBody;
    // used to prevent onSuccess being called when no exception has been thrown but the command failed anyway
    private boolean isFailed;

    public AbstractCommand(CommandContribution commandContribution,
                           CommandContext context,
                           CommandManager commandManager,
                           String commandString,
                           boolean requiresInput,
                           String identifier,
                           String description,
                           Category category) {
        this.commandContribution = commandContribution;
        this.context = context;
        this.commandManager = commandManager;
        this.requiresInput = requiresInput;
        this.identifier = identifier;
        this.description = description;
        this.category = category;
        this.argumentContribution = setupArguments();
        this.messageService = new MessageService();

        processCommand(commandString);
    }

    /**
     * The actual logic to run for this command
     *
     * @throws Exception any exception thrown during execution
     */
    public abstract void doRun() throws Exception;

    /**
     * Method called when {@link #isFailed()} is false and no exception has been thrown. Usually sends a success message
     * to Discord.
     */
    public abstract void onSuccess();

    /**
     * Run logic with the given user choice after a {@link ClientQuestionEvent} has been completed. Called by
     * {@link AnswerCommand}.
     *
     * @param chosenOption
     */
    public void withUserResponse(Object chosenOption) throws Exception {
    }

    public void verify() {
        if (requiresInput() && getCommandBody().isBlank()) {
            throw new InvalidCommandException("That command requires more input!");
        }

        getArgumentContribution().complete();
    }

    /**
     * define the arguments that this command accepts
     *
     * @return the {@link ArgumentContribution}
     */
    public ArgumentContribution setupArguments() {
        return new ArgumentContribution(this);
    }

    public ArgumentContribution getArgumentContribution() {
        return argumentContribution;
    }

    public void invoke(CheckedRunnable runnable) {
        Invoker invoker = getContext().getGuildContext().getInvoker();
        invoker.invoke(getContext().getSession(), runnable);
    }

    public <E> E invoke(Callable<E> callable) {
        Invoker invoker = getContext().getGuildContext().getInvoker();
        return invoker.invoke(getContext().getSession(), callable);
    }

    public CommandContext getContext() {
        return context;
    }

    /**
     * Run code after setting the spotify access token to the one of the current user's login. This is required for
     * commands that do not necessarily require a login, in which case the access token will always be set before
     * executing the command, but might need to access a user's data under some condition.
     *
     * @param user the user whose spotify data is being accessed, needs to be logged in
     * @param callable the code to run
     */
    protected <E> E runWithLogin(User user, Callable<E> callable) throws Exception {
        Invoker invoker = getContext().getGuildContext().getInvoker();
        Login login = getManager().getLoginManager().requireLoginForUser(user);
        return invoker.runForUser(login, getContext().getSpotifyApi(), callable);
    }

    /**
     * Run a callable with the default spotify credentials. Used for spotify api queries in commands where
     * requiresCredentials is false.
     */
    protected <E> E runWithCredentials(Callable<E> callable) throws Exception {
        Invoker invoker = getContext().getGuildContext().getInvoker();
        return invoker.runWithCredentials(getContext().getSpotifyApi(), callable);
    }

    protected boolean argumentSet(String argument) {
        return argumentContribution.argumentSet(argument);
    }

    protected <E> E getArgumentValue(String argument, Class<E> type) {
        return argumentContribution.getArgument(argument).getValue(type);
    }

    protected boolean isPartitioned() {
        DiscordListener.Mode mode = commandManager.getDiscordListener().getMode();
        return mode == DiscordListener.Mode.PARTITIONED;
    }

    /**
     * askQuestion implementation that uses the index of the option as its key and accepts a function to get the display
     * for each option
     *
     * @param options the available options
     * @param displayFunc the function that returns the display for each option (e.g. for a Track it should be a function
     * that returns the track's name and artists)
     * @param <O> the type of options
     */
    protected <O> void askQuestion(List<O> options, Function<O, String> displayFunc) {
        ClientQuestionEvent question = new ClientQuestionEvent(this);
        for (int i = 0; i < options.size(); i++) {
            O option = options.get(i);
            question.mapOption(String.valueOf(i), option, displayFunc.apply(option));
        }
        askQuestion(question);
    }

    /**
     * like {@link #askQuestion(List, Function)} but adds the result of the given detailFunc to each option if several
     * options have the same display
     */
    protected <O> void askQuestion(List<O> options, Function<O, String> displayFunc, Function<O, String> detailFunc) {
        Map<O, String> optionWithDisplay = new LinkedHashMap<>();
        Set<O> optionsWithDuplicateDisplay = Sets.newHashSet();
        for (O option : options) {
            String display = displayFunc.apply(option);
            if (optionWithDisplay.containsValue(display)) {
                optionsWithDuplicateDisplay.addAll(Util.getKeysForValue(optionWithDisplay, display));
                optionsWithDuplicateDisplay.add(option);
            }

            // naively put the display in the map no matter if it already exists. This will retain order in the LinkedMap
            // and make sure the detail part does not get added twice if two options have the same detail display
            optionWithDisplay.put(option, display);
        }

        for (O o : optionsWithDuplicateDisplay) {
            String display = optionWithDisplay.get(o);
            optionWithDisplay.put(o, display + " (" + detailFunc.apply(o) + ")");
        }

        askQuestion(options, optionWithDisplay::get);
    }

    protected void askQuestion(ClientQuestionEvent question) {
        setFailed(true);
        commandManager.addQuestion(question);
        question.ask();
    }

    protected void sendMessage(MessageChannel channel, String message) {
        messageService.send(message, channel);
    }

    protected void sendMessage(MessageChannel channel, MessageEmbed message) {
        messageService.send(message, channel);
    }

    protected void sendMessage(User user, String message) {
        messageService.send(message, user);
    }

    protected void sendWrapped(String message, String wrapper, MessageChannel channel) {
        messageService.sendWrapped(message, wrapper, channel);
    }

    protected CompletableFuture<Message> sendMessage(MessageChannel channel, InputStream file, String fileName, MessageBuilder messageBuilder) {
        return messageService.send(messageBuilder, file, fileName, channel);
    }

    protected CompletableFuture<Message> sendWithLogo(MessageChannel channel, EmbedBuilder embedBuilder) throws IOException {
        return messageService.sendWithLogo(embedBuilder, channel);
    }

    protected CompletableFuture<Message> sendSuccess(MessageChannel channel, String message) {
        return messageService.sendSuccess(message, channel);
    }

    /**
     * Used for any command with an A $to B syntax. Requires the command body to have exactly one argument preceded by
     * $.
     *
     * @return both halves of the logical two sided statement
     */
    protected Pair<String, String> splitInlineArgument(String argument) {
        StringList words = StringListImpl.create(getCommandBody(), " ");

        List<Integer> positions = words.findPositionsOf("$" + argument, true);
        if (positions.isEmpty()) {
            throw new InvalidCommandException("Expected argument: " + argument);
        }
        int position = positions.get(0);

        if (position == 0 || position == words.size() - 1) {
            throw new InvalidCommandException("No input before or after inline argument: " + argument);
        }

        String left = words.subList(0, position).toSeparatedString(" ");
        String right = words.subList(position + 1, words.size()).toSeparatedString(" ");

        if (left.isBlank() || right.isBlank()) {
            throw new InvalidCommandException("No input before or after inline argument: " + argument);
        }

        return Pair.of(left.trim(), right.trim());
    }

    /**
     * @return the String this command gets referenced with
     */
    public String getIdentifier() {
        return identifier;
    }

    public Category getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public CommandManager getManager() {
        return commandManager;
    }

    public String getCommandBody() {
        return commandBody;
    }

    public boolean isFailed() {
        return isFailed;
    }

    public void setFailed(boolean isFailed) {
        this.isFailed = isFailed;
    }

    public boolean requiresInput() {
        return requiresInput;
    }

    private void processCommand(String commandString) {
        StringList words = StringListImpl.separateString(commandString, " ");

        int commandBodyIndex = 0;
        for (String word : words) {
            if (word.startsWith("$")) {
                String argString = word.replaceFirst("\\$", "");
                // check if the argument has an assigned value
                int equalsIndex = argString.indexOf("=");
                if (equalsIndex > -1) {
                    if (equalsIndex == 0 || equalsIndex == word.length() - 1) {
                        throw new InvalidCommandException("Malformed argument. Equals sign cannot be first or last character.");
                    }
                    String argument = argString.substring(0, equalsIndex);
                    String value = argString.substring(equalsIndex + 1);
                    argumentContribution.setArgument(argument.toLowerCase(), value);
                } else {
                    argumentContribution.setArgument(argString.toLowerCase());
                }
            } else if (!word.isBlank()) {
                break;
            }
            commandBodyIndex += word.length();
        }

        commandBody = words.toString().substring(commandBodyIndex).trim();
    }

    public CommandContribution getCommandContribution() {
        return commandContribution;
    }

    public enum Category {

        PLAYBACK("playback", "Commands that manage the music playback"),
        PLAYLIST_MANAGEMENT("playlist management", "Commands that add or remove items from botify playlists"),
        SPOTIFY("spotify", "Commands that manage the Spotify login or upload playlists to Spotify"),
        SEARCH("search", "Commands that search for botify playlists or list all of them or search for Spotify and Youtube tracks, videos and playlists"),
        GENERAL("general", "General commands that manage this bot");

        private final String name;
        private final String description;

        Category(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

}
