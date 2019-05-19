package net.robinfriedli.botify.discord;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.requests.restaction.MessageAction;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

public class MessageService {

    private final int limit;
    private final Logger logger;

    public MessageService() {
        this.logger = LoggerFactory.getLogger(getClass());
        limit = 1000;
    }

    public MessageService(int limit) {
        this.limit = limit;
        this.logger = LoggerFactory.getLogger(getClass());
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

    public CompletableFuture<Message> send(String message, Guild guild) {
        return acceptForGuild(guild, messageChannel -> messageChannel.sendMessage(message));
    }

    public CompletableFuture<Message> send(MessageEmbed messageEmbed, MessageChannel messageChannel) {
        return sendInternal(messageChannel, messageEmbed);
    }

    public CompletableFuture<Message> send(MessageEmbed messageEmbed, Guild guild) {
        return acceptForGuild(guild, channel -> channel.sendMessage(messageEmbed));
    }

    public CompletableFuture<Message> sendWithLogo(EmbedBuilder embedBuilder, MessageChannel channel) throws IOException {
        MessageBuilder messageBuilder = new MessageBuilder();
        String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");
        InputStream file = new URL(baseUri + "/resources-public/img/botify-logo-small.png").openStream();
        embedBuilder.setThumbnail("attachment://logo.png");
        embedBuilder.setColor(Color.decode("#1DB954"));
        messageBuilder.setEmbed(embedBuilder.build());
        return send(messageBuilder, file, "logo.png", channel);
    }

    public void sendWithLogo(EmbedBuilder embedBuilder, Guild guild) throws IOException {
        MessageBuilder messageBuilder = new MessageBuilder();
        String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");
        InputStream file = new URL(baseUri + "/resources-public/img/botify-logo.png").openStream();
        embedBuilder.setThumbnail("attachment://logo.png");
        embedBuilder.setColor(Color.decode("#1DB954"));
        messageBuilder.setEmbed(embedBuilder.build());
        send(messageBuilder, file, "logo.png", guild);
    }

    public CompletableFuture<Message> send(MessageBuilder messageBuilder, InputStream file, String fileName, MessageChannel messageChannel) {
        return accept(messageChannel, c -> c.sendFile(file, fileName, messageBuilder.build()));
    }

    public CompletableFuture<Message> send(MessageBuilder messageBuilder, InputStream file, String fileName, Guild guild) {
        return acceptForGuild(guild, c -> c.sendFile(file, fileName, messageBuilder.build()));
    }

    public CompletableFuture<Message> sendSuccess(String message, MessageChannel channel) {
        return sendBoxed("Success", message, Color.GREEN, channel);
    }

    public CompletableFuture<Message> sendError(String message, MessageChannel channel) {
        return sendBoxed("Error", message, Color.RED, channel);
    }

    public CompletableFuture<Message> sendException(String message, MessageChannel channel) {
        return sendBoxed("Exception", message, Color.RED, channel);
    }

    public CompletableFuture<Message> sendBoxed(String title, String message, Color color, MessageChannel channel) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(color);
        embedBuilder.setTitle(title);
        embedBuilder.setDescription(message);
        return send(embedBuilder.build(), channel);
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

    private CompletableFuture<Message> sendInternal(MessageChannel channel, String text) {
        return accept(channel, c -> c.sendMessage(text));
    }

    private CompletableFuture<Message> sendInternal(MessageChannel channel, MessageEmbed messageEmbed) {
        return accept(channel, c -> c.sendMessage(messageEmbed));
    }

    private CompletableFuture<Message> accept(MessageChannel channel, Function<MessageChannel, MessageAction> function) {
        CompletableFuture<Message> futureMessage = new CompletableFuture<>();
        try {
            MessageAction messageAction = function.apply(channel);
            messageAction.queue(futureMessage::complete, e -> logger.error("Error sending message", e));
        } catch (InsufficientPermissionException e) {
            Permission permission = e.getPermission();
            if (permission == Permission.MESSAGE_WRITE) {
                if (channel instanceof TextChannel && canTalk(((TextChannel) channel).getGuild())) {
                    Guild guild = ((TextChannel) channel).getGuild();
                    send("I do not have permission to send any messages to channel " + channel.getName() + " so I'll send it here instead.", guild);
                    acceptForGuild(guild, function);
                } else if (channel instanceof TextChannel) {
                    logger.warn("Unable to send messages to guild " + ((TextChannel) channel).getGuild());
                } else {
                    logger.warn("Unable to send messages to " + channel);
                }
            } else {
                send("Bot is missing permission: " + permission.getName(), channel);
            }
        }

        return futureMessage;
    }

    private CompletableFuture<Message> acceptForGuild(Guild guild, Function<MessageChannel, MessageAction> function) {
        TextChannel defaultChannel = guild.getDefaultChannel();
        if (defaultChannel != null && defaultChannel.canTalk()) {
            return accept(defaultChannel, function);
        } else {
            TextChannel systemChannel = guild.getSystemChannel();
            if (systemChannel != null && systemChannel.canTalk()) {
                return accept(systemChannel, function);
            }
        }

        List<TextChannel> availableChannels = guild.getTextChannels().stream().filter(TextChannel::canTalk).collect(Collectors.toList());
        if (availableChannels.isEmpty()) {
            logger.warn("Unable to send any messages to guild " + guild.getName() + " (" + guild.getId() + ")");
            return CompletableFuture.completedFuture(null);
        } else {
            return accept(availableChannels.get(0), function);
        }
    }

    private boolean canTalk(Guild guild) {
        return guild.getTextChannels().stream().anyMatch(TextChannel::canTalk);
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
