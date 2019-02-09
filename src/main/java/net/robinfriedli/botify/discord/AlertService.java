package net.robinfriedli.botify.discord;

import java.util.List;

import org.slf4j.Logger;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

/**
 * TODO replace with Message class that allows wrapped blocks inside of message
 */
public class AlertService {

    private final int limit;
    private final Logger logger;

    public AlertService(Logger logger) {
        this.logger = logger;
        limit = 1000;
    }

    public AlertService(int limit, Logger logger) {
        this.limit = limit;
        this.logger = logger;
    }

    public void send(String message, MessageChannel channel) {
        if (message.length() < limit) {
            sendInternal(channel, message);
        } else {
            List<String> outputParts = separateMessage(message);
            outputParts.forEach(part -> sendInternal(channel, part));
        }
    }

    public void send(String message, User user) {
        if (message.length() < limit) {
            user.openPrivateChannel().queue(channel -> sendInternal(channel, message));
        } else {
            List<String> outputParts = separateMessage(message);
            outputParts.forEach(part -> user.openPrivateChannel().queue(channel -> sendInternal(channel, part)));
        }
    }

    public void send(MessageEmbed messageEmbed, MessageChannel messageChannel) {
        messageChannel.sendMessage(messageEmbed).queue();
    }

    public void sendWrapped(String message, String wrapper, MessageChannel channel) {
        if (message.length() < limit) {
            sendInternal(channel, wrapper + message + wrapper);
        } else {
            List<String> outputParts = separateMessage(message);
            outputParts.forEach(part -> sendInternal(channel, wrapper + part + wrapper));
        }
    }

    public void sendWrapped(String message, String wrapper, User user) {
        if (message.length() < limit) {
            user.openPrivateChannel().queue(channel -> sendInternal(channel, wrapper + message + wrapper));
        } else {
            List<String> outputParts = separateMessage(message);
            outputParts.forEach(part -> user.openPrivateChannel().queue(channel -> sendInternal(channel, wrapper + part + wrapper)));
        }
    }

    private void sendInternal(MessageChannel channel, String text) {
        try {
            channel.sendMessage(text).queue();
        } catch (InsufficientPermissionException e) {
            StringBuilder messageBuilder = new StringBuilder("Insufficient permissions to send messages to channel" + channel.getName());
            if (channel instanceof TextChannel) {
                messageBuilder.append(" on guild ").append(((TextChannel) channel).getGuild().getName());
            }
            logger.warn(messageBuilder.toString(), e);
        }
    }

    private List<String> separateMessage(String message) {
        List<String> outputParts = Lists.newArrayList();
        StringList paragraphs = StringListImpl.separateString(message, "\n");

        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i);
            if (paragraph.length() + System.lineSeparator().length() < limit) {
                // check that paragraph is not an empty line
                if (!paragraph.isBlank()) {
                    if (i < paragraphs.size() - 1) paragraph = paragraph + System.lineSeparator();
                    fillPart(outputParts, paragraph);
                }
            } else {
                // if the paragraph is too long separate into sentences
                StringList sentences = StringListImpl.separateString(paragraph, "\\. ");
                for (String sentence : sentences) {
                    if (sentence.length() < limit) {
                        fillPart(outputParts, sentence);
                    } else {
                        // if the sentence is too long split into words
                        StringList words = StringListImpl.separateString(sentence, " ");

                        for (String word : words) {
                            if (word.length() < limit) {
                                fillPart(outputParts, word);
                            } else {
                                StringList chars = StringListImpl.charsToList(word);
                                for (String charString : chars) {
                                    fillPart(outputParts, charString);
                                }
                            }
                        }
                    }
                }
            }
        }

        return outputParts;
    }

    private void fillPart(List<String> outputParts, String s) {
        if (outputParts.isEmpty()) {
            outputParts.add("");
        }

        int currentPart = outputParts.size() - 1;

        if (outputParts.get(currentPart).length() + s.length() < limit) {
            outputParts.set(currentPart, outputParts.get(currentPart) + s);
        } else {
            outputParts.add(s);
        }
    }

}
