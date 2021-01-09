package net.robinfriedli.botify.command;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.command.widgets.WidgetManager;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
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
    private final MessageChannel channel;
    private final Guild guild;
    private final String guildId;
    private final Logger logger;
    // if the message has been deleted by a widget action that does not always require a reset
    private boolean messageDeleted;

    protected AbstractWidget(WidgetManager widgetManager, Message message) {
        Botify botify = Botify.get();
        this.commandManager = botify.getCommandManager();
        this.executionQueueManager = botify.getExecutionQueueManager();
        this.widgetManager = widgetManager;
        this.message = message;
        this.channel = message.getChannel();
        this.guild = message.getGuild();
        this.guildId = message.getGuild().getId();
        widgetContribution = widgetManager.getContributionForWidget(getClass());
        logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Resets (i.e. re-sends) the message output after the widget action has been executed
     */
    public abstract void reset();

    public Message getMessage() {
        return message;
    }

    public MessageChannel getChannel() {
        return channel;
    }

    /**
     * Adds the reactions representing each available action to the message and prepares all actions
     */
    public void setup() {
        List<WidgetContribution.WidgetActionContribution> actionContributions =
            widgetContribution.getSubElementsWithType(WidgetContribution.WidgetActionContribution.class);

        Member selfMember = guild.getSelfMember();
        if (channel instanceof TextChannel) {
            if (!selfMember.hasPermission((TextChannel) channel, Permission.MESSAGE_ADD_REACTION)) {
                throw new UserException("Bot is missing permission to add reactions");
            }
        }

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
        if (!isMessageDeleted()) {
            try {
                try {
                    message.delete().queue();
                } catch (InsufficientPermissionException ignored) {
                    message.clearReactions().queue();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public void handleReaction(GuildMessageReactionAddEvent event, CommandContext context) {
        MessageReaction reaction = event.getReaction();
        Optional<AbstractWidgetAction> actionOptional = widgetContribution
            .getSubElementsWithType(WidgetContribution.WidgetActionContribution.class)
            .stream()
            .filter(actionContribution -> actionContribution.getEmojiUnicode().equals(reaction.getReactionEmote().getName()))
            .findAny()
            .map(actionContribution -> actionContribution.instantiate(context, this, event));
        actionOptional.ifPresent(abstractWidgetAction ->
            commandManager.runCommand(abstractWidgetAction, executionQueueManager.getForGuild(event.getGuild())));
    }

    public WidgetManager getWidgetManager() {
        return widgetManager;
    }

    public boolean isMessageDeleted() {
        return messageDeleted;
    }

    public void setMessageDeleted(boolean messageDeleted) {
        this.messageDeleted = messageDeleted;
    }

    public Guild getGuild() {
        return guild;
    }

    public String getGuildId() {
        return guildId;
    }

}
