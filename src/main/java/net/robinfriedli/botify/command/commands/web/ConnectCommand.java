package net.robinfriedli.botify.command.commands.web;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.concurrent.CompletableFutures;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.ClientSession;
import net.robinfriedli.botify.entities.GeneratedToken;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.ExceptionUtils;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.exec.Mode;
import net.robinfriedli.exec.MutexSync;
import net.robinfriedli.exec.modes.MutexSyncMode;

public class ConnectCommand extends AbstractCommand {

    private static final int RETRY_COUNT = 5;

    private static final MutexSync<UUID> TOKEN_SYNC = new MutexSync<>();
    private static final Map<Long, Object> USERS_WITH_PENDING_CONNECTION = new ConcurrentHashMap<>();
    private static final Object DUMMY = new Object();

    public ConnectCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        CommandContext context = getContext();

        Guild guild = context.getGuild();
        User user = context.getUser();
        long guildId = guild.getIdLong();
        long userId = user.getIdLong();
        long textChannelId = context.getChannel().getIdLong();

        if (USERS_WITH_PENDING_CONNECTION.put(userId, DUMMY) != null) {
            throw new InvalidCommandException("The bot is already waiting to receive the token in the DMs.");
        }

        ClientSession clientSession = new ClientSession();
        clientSession.setGuildId(guildId);
        clientSession.setUserId(userId);
        clientSession.setTextChannelId(textChannelId);

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setDescription("Send me the token shown by the web client as a direct message");
        getMessageService().sendTemporary(embedBuilder, context.getChannel());

        awaitPrivateMessage(clientSession, userId, guild, user, 1);
    }

    private void awaitPrivateMessage(ClientSession clientSession, long userId, Guild guild, User user, int attemptNumber) {
        CompletableFuture<PrivateMessageReceivedEvent> futurePrivateMessage = getManager().getEventWaiter()
            .awaitEvent(PrivateMessageReceivedEvent.class, event -> event.getAuthor().getIdLong() == userId)
            .orTimeout(1, TimeUnit.MINUTES);
        CompletableFutures.handleWhenComplete(futurePrivateMessage, (event, error) -> {
            try {
                MessageService messageService = getMessageService();
                if (event != null) {
                    String token = event.getMessage().getContentRaw();

                    UUID sessionId;
                    try {
                        sessionId = UUID.fromString(token);
                    } catch (IllegalArgumentException e) {
                        String message = String.format("'%s' is not a valid token. ", token);
                        if (attemptNumber >= RETRY_COUNT) {
                            messageService.sendError(message + "Maximum retry count reached.", user);
                        } else {
                            messageService.sendError(message + String.format("Attempt %d / %d", attemptNumber, RETRY_COUNT), user);
                            awaitPrivateMessage(clientSession, userId, guild, user, attemptNumber + 1);
                        }
                        return;
                    }

                    QueryBuilderFactory queryBuilderFactory = getQueryBuilderFactory();
                    MutexSyncMode<UUID> mutexSyncMode = new MutexSyncMode<>(sessionId, TOKEN_SYNC);
                    HibernateInvoker.create().invokeConsumer(Mode.create().with(mutexSyncMode), session -> {
                        Optional<GeneratedToken> foundGeneratedToken = queryBuilderFactory.find(GeneratedToken.class)
                            .where((cb, root) -> cb.equal(root.get("token"), sessionId))
                            .build(session)
                            .uniqueResultOptional();

                        if (foundGeneratedToken.isEmpty()) {
                            String message = "Token is invalid. Make sure it matches the token generated by your web client. Tokens may not be shared or reused. ";
                            if (attemptNumber >= RETRY_COUNT) {
                                messageService.sendError(message + "Maximum retry count reached.", user);
                            } else {
                                messageService.sendError(message + String.format("Attempt %d / %d", attemptNumber, RETRY_COUNT), user);
                                awaitPrivateMessage(clientSession, userId, guild, user, attemptNumber + 1);
                            }
                            return;
                        }

                        GeneratedToken generatedToken = foundGeneratedToken.get();
                        Long existingSessionCount = queryBuilderFactory.select(ClientSession.class, (from, cb) -> cb.count(from.get("pk")), Long.class)
                            .where((cb, root) -> cb.equal(root.get("sessionId"), sessionId))
                            .build(session)
                            .uniqueResult();
                        if (existingSessionCount > 0) {
                            messageService.sendError("A session with this ID already exists. You are probably already signed in, try reloading your web client. If your client still can't sign in try generating a new token using the reload button and then try the connect command again.", user);
                            return;
                        }

                        clientSession.setLastRefresh(LocalDateTime.now());
                        clientSession.setSessionId(sessionId);
                        clientSession.setIpAddress(generatedToken.getIp());
                        session.persist(clientSession);
                        session.delete(generatedToken);
                        messageService.sendSuccess(String.format("Okay, a session connected to guild '%s' " +
                            "has been prepared. You may return to the web client to complete the setup. The client should " +
                            "connect automatically, else you can continue manually.", guild.getName()), user);
                    });
                } else if (error != null) {
                    if (error instanceof TimeoutException) {
                        messageService.sendError("Your connection attempt timed out", user);
                    } else {
                        EmbedBuilder exceptionEmbed = ExceptionUtils.buildErrorEmbed(error);
                        exceptionEmbed.setTitle("Exception");
                        exceptionEmbed.setDescription("There has been an error awaiting private message to establish connection");
                        LoggerFactory.getLogger(getClass()).error("unexpected exception while awaiting private message", error);
                        sendMessage(exceptionEmbed.build());
                    }
                    setFailed(true);
                }
            } finally {
                USERS_WITH_PENDING_CONNECTION.remove(userId);
            }
        }, e -> {
            LoggerFactory.getLogger(getClass()).error("Unexpected error in whenComplete of event handler", e);
            EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(e)
                .setTitle("Exception")
                .setDescription("There has been an unexpected exception while trying to establish the connection");
            getMessageService().send(embedBuilder.build(), user);
        });
    }

    @Override
    public void onSuccess() {
    }

}
