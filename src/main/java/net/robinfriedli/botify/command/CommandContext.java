package net.robinfriedli.botify.command;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;

public class CommandContext {

    private final String commandBody;
    private final Message message;

    public CommandContext(String namePrefix, Message message) {
        this.commandBody = message.getContentDisplay().substring(namePrefix.length()).trim();
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }

    public User getUser() {
        return message.getAuthor();
    }

    public Guild getGuild() {
        return message.getGuild();
    }

    public MessageChannel getChannel() {
        return message.getChannel();
    }

    public JDA getJda() {
        return message.getJDA();
    }

    public String getCommandBody() {
        return commandBody;
    }
}
