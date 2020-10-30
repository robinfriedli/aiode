package net.robinfriedli.botify.command;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.xml.WidgetContribution;
import net.robinfriedli.botify.exceptions.UserException;

/**
 * A widget is an interactive message that can be interacted with by adding reactions. Different reactions trigger
 * different actions that are represented by the reaction's emote.
 */
public abstract class AbstractWidget {

    private final CommandManager commandManager;
    private final CommandExecutionQueueManager executionQueueManager;
    private final WidgetContribution widgetContribution;
    private final WidgetManager widgetManager;
    private final Message message;
    private final String guildId;
    private final Logger logger;

    private volatile CompletableFuture<Boolean> pendingMessageDeletion;
    private volatile boolean messageDeleted;

    protected AbstractWidget(WidgetManager widgetManager, Message message) {
        Botify botify = Botify.get();
        this.commandManager = botify.getCommandManager();
        this.executionQueueManager = botify.getExecutionQueueManager();
        this.widgetManager = widgetManager;
        this.message = message;
        this.guildId = message.getGuild().getId();
        widgetContribution = widgetManager.getContributionForWidget(getClass());
        logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Resets (i.e. re-sends) the message output after the widget action has been executed. The new message needs to be
     * attached to a new instance of this widget.
     */
    public abstract void reset();

    public Message getMessage() {
        return message;
    }

    /**
     * Adds the reactions representing each available action to the message and prepares all actions
     */
    public void setup() {
        List<WidgetContribution.WidgetActionContribution> actionContributions =
            widgetContribution.getSubElementsWithType(WidgetContribution.WidgetActionContribution.class);
        try {
            CompletableFuture<RuntimeException> futureException = new CompletableFuture<>();
            for (int i = 0; i < actionContributions.size(); i++) {
                WidgetContribution.WidgetActionContribution action = actionContributions.get(i);
                int finalI = i;
                message.addReaction(action.getEmojiUnicode()).queue(aVoid -> {
                    if (finalI == actionContributions.size() - 1 && !futureException.isDone()) {
                        futureException.cancel(false);
                    }
                }, throwable -> {
                    if (throwable instanceof InsufficientPermissionException || throwable instanceof ErrorResponseException) {
                        if (!futureException.isDone()) {
                            futureException.complete((RuntimeException) throwable);
                        }
                    } else {
                        logger.warn("Unexpected exception while adding reaction", throwable);
                    }
                });
            }

            futureException.thenAccept(e -> {
                MessageService messageService = Botify.get().getMessageService();
                if (e instanceof InsufficientPermissionException) {
                    messageService.sendError("Bot is missing permission: "
                        + ((InsufficientPermissionException) e).getPermission().getName(), message.getChannel());
                } else if (e instanceof ErrorResponseException) {
                    int errorCode = ((ErrorResponseException) e).getErrorCode();
                    if (errorCode == 50013) {
                        messageService.sendError("Bot is missing permission to add reactions", message.getChannel());
                    } else if (errorCode != 10008) {
                        // ignore errors thrown when the message has been deleted
                        logger.warn("Could not add reaction to message " + message, e);
                    }
                }
            });
        } catch (InsufficientPermissionException e) {
            // exception is actually never thrown when it should be, remove completable future hack if this ever changes
            throw new UserException("Bot is missing permission: " + e.getPermission().getName(), e);
        } catch (ErrorResponseException e) {
            if (e.getErrorCode() == 50013) {
                throw new UserException("Bot is missing permission to add reactions");
            } else {
                logger.warn("Could not add reaction to message " + message, e);
            }
        }
    }

    public void destroy() {
        widgetManager.removeWidget(this);
        awaitMessageDeletionIfPendingThenDo(deleted -> {
            if (!deleted) {
                try {
                    try {
                        message.delete().queue();
                    } catch (InsufficientPermissionException ignored) {
                        message.clearReactions().queue();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void handleReaction(GuildMessageReactionAddEvent event, CommandContext context) {
        MessageReaction reaction = event.getReaction();
        Optional<AbstractWidgetAction> actionOptional = widgetContribution
            .getSubElementsWithType(WidgetContribution.WidgetActionContribution.class)
            .stream()
            .filter(actionContribution -> actionContribution.getEmojiUnicode().equals(reaction.getReactionEmote().getName()))
            .findAny()
            .map(actionContribution -> actionContribution.instantiate(context, this, event));
        actionOptional.ifPresent(commandManager::runCommand);
    }

    public WidgetManager getWidgetManager() {
        return widgetManager;
    }

    /**
     * This method is used to run any logic that checks if this widget has deleted its associated message. If the widget
     * is currently in the process of deleting the message, i.e. if pendingMessageDeletion is set to a non completed future
     * but not null, this will execute the given code after the pendingMessageDeletion future is complete. Else this
     * simply checks the value of the messageDeleted field and apply it to the given consumer.
     *
     * @param resultConsumer The logic to run that depends on whether or not the message of this widget has been deleted.
     */
    public void awaitMessageDeletionIfPendingThenDo(Consumer<Boolean> resultConsumer) {
        if (pendingMessageDeletion != null) {
            pendingMessageDeletion.thenAccept(resultConsumer);
        } else {
            resultConsumer.accept(messageDeleted);
        }
    }

    public void setMessageDeleted(boolean messageDeleted) {
        // calls to #awaitMessageDeletionIfPendingThenDo while the deletion is in progress will await the completable future
        // and see the completion of the completable future instance; calls made to that method after deletion will see
        // the altered messageDeleted field; the pending deletion future is set back to null for in case this widget sends
        // a new message, i.e. after resetting
        this.messageDeleted = messageDeleted;

        if (pendingMessageDeletion != null) {
            pendingMessageDeletion.complete(messageDeleted);
            pendingMessageDeletion = null;
        }
    }

    public void awaitMessageDeletion() {
        pendingMessageDeletion = new CompletableFuture<>();
    }

    public String getGuildId() {
        return guildId;
    }

}
