package net.robinfriedli.botify.command.widgets;

import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;

/**
 * Abstract class to implement for any action that may be added to widget. Maps the action to the given emoji that will
 * be added to the widget and handles required permissions.
 */
public abstract class AbstractWidgetAction implements Command {

    private final AbstractWidget widget;
    private final CommandContext context;
    private final String identifier;
    private final String emojiUnicode;
    private final GuildMessageReactionAddEvent event;
    private boolean resetRequired;
    private CommandExecutionThread thread;

    /**
     * This constructor is called when instantiating the widget action reflectively, all implementations must have
     * this constructor.
     *
     * @param identifier    the identifier for this action, typically corresponds to an {@link AbstractCommand}. Relevant for permissions.
     * @param emojiUnicode  the emoji this action is mapped to that will trigger this action upon a reaction is added.
     * @param resetRequired whether or not the widget needs to resend its content after this action is done
     * @param context       the {@link CommandContext}
     * @param widget        the parent widget
     * @param event         the event that triggered this action
     */
    protected AbstractWidgetAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event) {
        this.widget = widget;
        this.context = context;
        this.identifier = identifier;
        this.emojiUnicode = emojiUnicode;
        this.resetRequired = resetRequired;
        this.event = event;
    }

    @Override
    public void onSuccess() {
        if (resetRequired) {
            widget.reset();
        }

        if (!widget.isMessageDeleted()) {
            removeReaction();
        }
    }

    @Override
    public void onFailure() {
        if (!widget.isMessageDeleted()) {
            removeReaction();
        }
    }

    @Override
    public boolean isFailed() {
        return false;
    }

    @Override
    public CommandContext getContext() {
        return context;
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }

    @Override
    public CommandExecutionThread getThread() {
        return thread;
    }

    @Override
    public void setThread(CommandExecutionThread thread) {
        this.thread = thread;
    }

    @Override
    public String getCommandBody() {
        return emojiUnicode;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String display() {
        return widget.getClass().getSimpleName() + "$" + getClass().getSimpleName();
    }

    public AbstractWidget getWidget() {
        return widget;
    }

    public String getEmojiUnicode() {
        return emojiUnicode;
    }

    public boolean isResetRequired() {
        return resetRequired;
    }

    public void setResetRequired(boolean resetRequired) {
        this.resetRequired = resetRequired;
    }

    private void removeReaction() {
        try {
            event.getReaction().removeReaction(getContext().getUser()).queue();
        } catch (InsufficientPermissionException ignored) {
        }
    }

}
