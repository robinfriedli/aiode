package net.robinfriedli.botify.command;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.command.widgets.ActionRunable;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.UserException;

/**
 * A widget is an interactive message that can be interacted with by adding reactions. Different reactions trigger
 * different actions that are represented by the reaction's emote.
 */
public abstract class AbstractWidget {

    private final CommandManager commandManager;
    private final Message message;
    private List<WidgetAction> actions;
    // if the message has been deleted by a widget action that does not always require a reset
    private boolean messageDeleted;

    protected AbstractWidget(CommandManager commandManager, Message message) {
        this.commandManager = commandManager;
        this.message = message;
    }

    public abstract List<WidgetAction> setupActions();

    /**
     * Resets (i.e. re-sends) the message output after the widget action has been executed
     */
    public abstract void reset() throws Exception;

    public Message getMessage() {
        return message;
    }

    /**
     * Adds the reactions representing each available action to the message and prepares all actions
     */
    public void setup() {
        this.actions = setupActions();
        try {
            CompletableFuture<Boolean> failed = new CompletableFuture<>();
            for (int i = 0; i < actions.size(); i++) {
                WidgetAction action = actions.get(i);
                int finalI = i;
                message.addReaction(action.getUnicode()).queueAfter(100, TimeUnit.MILLISECONDS, aVoid -> {
                    if (finalI == actions.size() - 1 && !failed.isDone()) {
                        failed.complete(false);
                    }
                }, throwable -> failed.complete(true));
            }

            failed.thenAccept(completedFailed -> {
                if (completedFailed) {
                    new MessageService().sendError("Bot is missing permission to add reactions", message.getTextChannel());
                }
            });
        } catch (InsufficientPermissionException e) {
            // exception is actually never thrown when it should be, remove completable future hack if this ever changes
            throw new UserException("Bot is missing permission: " + e.getPermission().getName(), e);
        }
    }

    public void destroy() {
        commandManager.removeWidget(this);
        try {
            if (!isMessageDeleted()) {
                message.clearReactions().queueAfter(3, TimeUnit.SECONDS);
            }
        } catch (Throwable ignored) {
        }
    }

    public void handleReaction(GuildMessageReactionAddEvent event) throws Exception {
        MessageReaction reaction = event.getReaction();
        User sourceUser = event.getUser();
        Optional<WidgetAction> actionOptional = actions.stream()
            .filter(widgetAction -> widgetAction.getEmoteId().equals(reaction.getReactionEmote().getId())
                || widgetAction.getUnicode().equals(reaction.getReactionEmote().getName())).findAny();
        if (actionOptional.isPresent()) {
            WidgetAction widgetAction = actionOptional.get();
            widgetAction.getAction().run(event);
            if (widgetAction.isResetRequired()) {
                reset();
            } else if (!isMessageDeleted()) {
                try {
                    reaction.removeReaction(sourceUser).queue();
                } catch (InsufficientPermissionException e) {
                    throw new UserException("Bot is missing permission: " + e.getPermission().getName());
                }
            }
        }
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public boolean isMessageDeleted() {
        return messageDeleted;
    }

    public void setMessageDeleted(boolean messageDeleted) {
        this.messageDeleted = messageDeleted;
    }

    public class WidgetAction {

        private final String emoteId;
        private final String unicode;
        private final ActionRunable action;
        private final boolean resetRequired;

        public WidgetAction(String emoteId, String unicode, ActionRunable action) {
            this(emoteId, unicode, action, false);
        }

        public WidgetAction(String emoteId, String unicode, ActionRunable action, boolean resetRequired) {
            this.emoteId = emoteId;
            this.unicode = unicode;
            this.action = action;
            this.resetRequired = resetRequired;
        }

        public String getEmoteId() {
            return emoteId;
        }

        public ActionRunable getAction() {
            return action;
        }

        public String getUnicode() {
            return unicode;
        }

        public boolean isResetRequired() {
            return resetRequired;
        }
    }

}
