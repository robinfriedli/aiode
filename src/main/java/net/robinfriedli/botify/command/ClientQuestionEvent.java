package net.robinfriedli.botify.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.properties.ColorSchemeProperty;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.Util;

/**
 * Represents a two sided conversation between user and bot triggered when a user enters an ambiguous command
 * E.g. a user enters a play command with a song name for which several tracks were found
 *
 * This gets destroyed as soon as the user answers this question, enters a different command that triggers a question
 * or 5 minutes pass
 */
public class ClientQuestionEvent {

    private final AbstractCommand sourceCommand;
    private final Timer destructionTimer;

    /**
     * the available answers for the user mapped to the option they represent
     */
    private Map<String, Option> options = new LinkedHashMap<>();

    public ClientQuestionEvent(AbstractCommand sourceCommand) {
        this.sourceCommand = sourceCommand;
        destructionTimer = new Timer();
        destructionTimer.schedule(new SelfDestructTask(), 300000);
    }

    public void mapOption(String key, Object option, String display) {
        options.put(key, new Option(option, display));
    }

    public Object get(String key) {
        Option chosenOption = options.get(key);

        if (chosenOption == null) {
            throw new InvalidCommandException("Unknown option " + key);
        }

        return chosenOption.getOption();
    }

    public <T> T get(String key, Class<T> target) {
        return target.cast(get(key));
    }

    public void ask() {
        ask("Several options found", "Choose an option using the answer command: $botify answer KEY (replace KEY with the key for your option)");
    }

    public void ask(String title, String description) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(title);
        embedBuilder.setDescription(description);
        embedBuilder.setColor(ColorSchemeProperty.getColor());

        Util.appendEmbedList(
            embedBuilder,
            options.keySet(),
            optionKey -> optionKey + " - " + options.get(optionKey).getDisplay(),
            "Key - Option"
        );

        new MessageService().send(embedBuilder.build(), sourceCommand.getContext().getChannel());
    }

    public void destroy() {
        sourceCommand.getContext().getGuildContext().removeQuestion(this);
        destructionTimer.cancel();
    }

    public AbstractCommand getSourceCommand() {
        return sourceCommand;
    }

    public CommandContext getCommandContext() {
        return sourceCommand.getContext();
    }

    public User getUser() {
        return getCommandContext().getUser();
    }

    public Guild getGuild() {
        return getCommandContext().getGuild();
    }

    public class Option {

        private final Object option;
        private final String display;

        public Option(Object option, String display) {
            this.option = option;
            this.display = display;
        }

        public Object getOption() {
            return option;
        }

        public String getDisplay() {
            return display;
        }
    }

    private class SelfDestructTask extends TimerTask {

        @Override
        public void run() {
            destroy();
        }
    }

}
