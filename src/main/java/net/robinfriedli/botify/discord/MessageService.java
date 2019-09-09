package net.robinfriedli.botify.discord;

import java.awt.Color;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.botify.discord.property.properties.TempMessageTimeoutProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class MessageService {

    private static final ScheduledExecutorService TEMP_MESSAGE_DELETION_SCHEDULER = Executors.newScheduledThreadPool(3);

    private final int limit;
    private final Logger logger;

    public MessageService() {
        this(1000);
    }

    public MessageService(int limit) {
        this.limit = limit;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    public CompletableFuture<Message> send(String message, MessageChannel channel) {
        if (message.length() < limit) {
            return sendInternal(channel, message);
        } else {
            List<String> outputParts = separateMessage(message);
            outputParts.forEach(part -> sendInternal(channel, part));
            CompletableFuture<Message> canceledFuture = new CompletableFuture<>();
            canceledFuture.cancel(true);
            return canceledFuture;
        }
    }

    public CompletableFuture<Message> send(String message, User user) {
        CompletableFuture<Message> futureMessage = new CompletableFuture<>();
        if (message.length() < limit) {
            user.openPrivateChannel().queue(channel -> {
                CompletableFuture<Message> future = sendInternal(channel, message);
                future.whenComplete((msg, e) -> {
                    if (msg != null) {
                        futureMessage.complete(msg);
                    } else {
                        futureMessage.completeExceptionally(e);
                    }
                });
            });
        } else {
            List<String> outputParts = separateMessage(message);
            outputParts.forEach(part -> user.openPrivateChannel().queue(channel -> sendInternal(channel, part)));
            futureMessage.cancel(false);
        }
        return futureMessage;
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

    public CompletableFuture<Message> send(EmbedBuilder embedBuilder, MessageChannel channel) {
        embedBuilder.setColor(ColorSchemeProperty.getColor());
        return send(embedBuilder.build(), channel);
    }

    public CompletableFuture<Message> send(EmbedBuilder embedBuilder, Guild guild) {
        embedBuilder.setColor(ColorSchemeProperty.getColor());
        return send(embedBuilder.build(), guild);
    }

    public CompletableFuture<Message> sendWithLogo(EmbedBuilder embedBuilder, MessageChannel channel) {
        String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");
        embedBuilder.setThumbnail(baseUri + "/resources-public/img/botify-logo-small.png");
        embedBuilder.setColor(ColorSchemeProperty.getColor());
        return send(embedBuilder.build(), channel);
    }

    public CompletableFuture<Message> sendWithLogo(EmbedBuilder embedBuilder, Guild guild) {
        String baseUri = PropertiesLoadingService.requireProperty("BASE_URI");
        embedBuilder.setThumbnail(baseUri + "/resources-public/img/botify-logo.png");
        embedBuilder.setColor(ColorSchemeProperty.getColor());
        return send(embedBuilder, guild);
    }

    public CompletableFuture<Message> send(MessageBuilder messageBuilder, InputStream file, String fileName, MessageChannel messageChannel) {
        return accept(messageChannel, c -> {
            MessageAction messageAction = c.sendMessage(messageBuilder.build());
            return messageAction.addFile(file, fileName);
        });
    }

    public CompletableFuture<Message> send(MessageBuilder messageBuilder, InputStream file, String fileName, Guild guild) {
        return acceptForGuild(guild, c -> {
            MessageAction messageAction = c.sendMessage(messageBuilder.build());
            return messageAction.addFile(file, fileName);
        });
    }

    public CompletableFuture<Message> sendSuccess(String message, MessageChannel channel) {
        return sendBoxed("Success", message, Color.GREEN, channel, true);
    }

    public CompletableFuture<Message> sendError(String message, MessageChannel channel) {
        return sendBoxed("Error", message, Color.RED, channel, true);
    }

    public CompletableFuture<Message> sendException(String message, MessageChannel channel) {
        return sendBoxed("Exception", message, Color.RED, channel, false);
    }

    public CompletableFuture<Message> sendBoxed(String title, String message, Color color, MessageChannel channel, boolean temporary) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(color);
        embedBuilder.setTitle(title);
        embedBuilder.setDescription(message);
        if (temporary) {
            return sendTemporary(embedBuilder.build(), channel);
        } else {
            return send(embedBuilder.build(), channel);
        }
    }

    public CompletableFuture<Message> sendTemporary(MessageEmbed messageEmbed, MessageChannel messageChannel) {
        CompletableFuture<Message> futureMessage = send(messageEmbed, messageChannel);
        futureMessage.thenAccept(message -> new TempMessageDeletionTask(message).schedule());
        return futureMessage;
    }

    public CompletableFuture<Message> sendTemporary(EmbedBuilder embedBuilder, MessageChannel messageChannel) {
        CompletableFuture<Message> futureMessage = send(embedBuilder, messageChannel);
        futureMessage.thenAccept(message -> new TempMessageDeletionTask(message).schedule());
        return futureMessage;
    }

    public CompletableFuture<Message> sendTemporary(String message, MessageChannel messageChannel) {
        CompletableFuture<Message> futureMessage = send(message, messageChannel);
        futureMessage.thenAccept(msg -> new TempMessageDeletionTask(msg).schedule());
        return futureMessage;
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

    public void sendToActiveGuilds(MessageEmbed message, JDA jda, AudioManager audioManager, Session session) {
        GuildManager guildManager = Botify.get().getGuildManager();
        Set<Guild> activeGuilds = guildManager.getActiveGuilds(session);

        logger.info("Sending message to " + activeGuilds.size() + " active guilds.");
        for (Guild activeGuild : activeGuilds) {
            if (activeGuild != null) {
                send(message, activeGuild);
            }
        }
    }

    public CompletableFuture<Message> accept(MessageChannel channel, Function<MessageChannel, MessageAction> function) {
        CompletableFuture<Message> futureMessage = new CompletableFuture<>();
        try {
            MessageAction messageAction = function.apply(channel);
            messageAction.queue(futureMessage::complete, e -> {
                handleError(e, channel);
                futureMessage.completeExceptionally(e);
            });
        } catch (InsufficientPermissionException e) {
            Permission permission = e.getPermission();
            if (permission == Permission.MESSAGE_WRITE) {
                if (channel instanceof TextChannel && canTalk(((TextChannel) channel).getGuild())) {
                    Guild guild = ((TextChannel) channel).getGuild();
                    send("I do not have permission to send any messages to channel " + channel.getName() + " so I'll send it here instead.", guild);
                    acceptForGuild(guild, function).thenAccept(futureMessage::complete);
                } else if (channel instanceof TextChannel) {
                    logger.warn("Unable to send messages to guild " + ((TextChannel) channel).getGuild());
                    futureMessage.completeExceptionally(e);
                } else {
                    logger.warn("Unable to send messages to " + channel);
                    futureMessage.completeExceptionally(e);
                }
            } else {
                StringBuilder errorMessage = new StringBuilder("Missing permission ").append(permission);
                if (channel instanceof TextChannel) {
                    errorMessage.append(" on guild ").append(((TextChannel) channel).getGuild());
                }
                logger.warn(errorMessage.toString());

                futureMessage.completeExceptionally(e);
                String message = "Bot is missing permission: " + permission.getName();
                send(message, channel);
            }
        }

        return futureMessage;
    }

    public CompletableFuture<Message> acceptForGuild(Guild guild, Function<MessageChannel, MessageAction> function) {
        TextChannel textChannel = getTextChannelForGuild(guild);

        if (textChannel == null) {
            logger.warn("Unable to send any messages to guild " + guild.getName() + " (" + guild.getId() + ")");
            return CompletableFuture.failedFuture(new CancellationException());
        } else {
            return accept(textChannel, function);
        }
    }

    private TextChannel getTextChannelForGuild(Guild guild) {
        Botify botify = Botify.get();
        GuildPropertyManager guildPropertyManager = botify.getGuildPropertyManager();
        GuildContext guildContext = botify.getGuildManager().getContextForGuild(guild);
        AbstractGuildProperty defaultTextChannelProperty = guildPropertyManager.getProperty("defaultTextChannelId");
        if (defaultTextChannelProperty != null) {
            String defaultTextChannelId = (String) StaticSessionProvider.invokeWithSession(session -> {
                return defaultTextChannelProperty.get(guildContext.getSpecification(session));
            });

            if (!Strings.isNullOrEmpty(defaultTextChannelId)) {
                TextChannel textChannelById = guild.getTextChannelById(defaultTextChannelId);
                if (textChannelById != null && textChannelById.canTalk()) {
                    return textChannelById;
                }
            }
        }

        TextChannel defaultChannel = guild.getDefaultChannel();
        if (defaultChannel != null && defaultChannel.canTalk()) {
            return defaultChannel;
        } else {
            TextChannel systemChannel = guild.getSystemChannel();
            if (systemChannel != null && systemChannel.canTalk()) {
                return systemChannel;
            }
        }

        List<TextChannel> availableChannels = guild.getTextChannels().stream().filter(TextChannel::canTalk).collect(Collectors.toList());
        if (availableChannels.isEmpty()) {
            return null;
        } else {
            return availableChannels.get(0);
        }
    }

    private CompletableFuture<Message> sendInternal(MessageChannel channel, String text) {
        return accept(channel, c -> c.sendMessage(text));
    }

    private CompletableFuture<Message> sendInternal(MessageChannel channel, MessageEmbed messageEmbed) {
        return accept(channel, c -> c.sendMessage(messageEmbed));
    }

    private boolean canTalk(Guild guild) {
        return guild.getTextChannels().stream().anyMatch(TextChannel::canTalk);
    }

    private void handleError(Throwable e, MessageChannel channel) {
        if (e instanceof ErrorResponseException) {
            if (e.getCause() instanceof SocketTimeoutException) {
                logger.warn("Timeout sending message to channel " + channel);
            } else {
                logger.warn(String.format("Error response msg: %s cause: %s: %s sending message to channel %s",
                    e.getMessage(),
                    e.getCause(),
                    e.getCause() != null ? e.getCause().getMessage() : "null",
                    channel));
            }
        } else {
            logger.error("Unexpected exception sending message to channel " + channel, e);
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

    private class TempMessageDeletionTask implements Runnable {

        private final Message message;

        private TempMessageDeletionTask(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            try {
                message.delete().queue(v -> {
                }, this::logError);
            } catch (InsufficientPermissionException e) {
                logger.warn(String.format("Insufficient permission to delete temp message %s on guild %s", message, message.getGuild()));
            } catch (Throwable e) {
                logError(e);
            }
        }

        void schedule() {
            int timeoutSeconds;
            try {
                timeoutSeconds = getTimeout();
            } catch (Throwable e) {
                logger.error("Exception loading tempMessageTimeout property", e);
                return;
            }

            if (timeoutSeconds > 0) {
                TEMP_MESSAGE_DELETION_SCHEDULER.schedule(this, timeoutSeconds, TimeUnit.SECONDS);
            }
        }

        private int getTimeout() {
            if (message.isFromType(ChannelType.TEXT)) {
                return StaticSessionProvider.invokeWithSession(session -> {
                    Botify botify = Botify.get();
                    GuildPropertyManager guildPropertyManager = botify.getGuildPropertyManager();
                    AbstractGuildProperty tempMessageTimeoutProperty = guildPropertyManager.getProperty("tempMessageTimeout");
                    if (tempMessageTimeoutProperty != null) {
                        Guild guild = message.getGuild();
                        GuildSpecification specification = botify.getGuildManager().getContextForGuild(guild).getSpecification(session);
                        return (int) tempMessageTimeoutProperty.get(specification);
                    }

                    return TempMessageTimeoutProperty.DEFAULT_FALLBACK;
                });
            }

            return TempMessageTimeoutProperty.DEFAULT_FALLBACK;
        }

        private void logError(Throwable e) {
            logger.warn(String.format("Unable to delete temp message %s on guild %s. %s: %s%s",
                message,
                message.getGuild(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                e.getCause() != null
                    ? String.format("; caused by %s: %s", e.getCause().getClass().getSimpleName(), e.getCause().getMessage())
                    : ""));
        }
    }

}
