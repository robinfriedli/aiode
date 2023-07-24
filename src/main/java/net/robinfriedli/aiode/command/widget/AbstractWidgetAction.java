package net.robinfriedli.aiode.command.widget;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.PermissionTarget;
import net.robinfriedli.aiode.concurrent.CommandExecutionTask;
import net.robinfriedli.aiode.entities.xml.WidgetContribution;

/**
 * Abstract class to implement for any action that may be added to widget. Maps the action to the given emoji that will
 * be added to the widget and handles required permissions.
 */
public abstract class AbstractWidgetAction implements Command {

    private final AbstractWidget widget;
    private final CommandContext context;
    private final String identifier;
    private final String emojiUnicode;
    private final ButtonInteractionEvent event;
    private final WidgetManager.WidgetActionDefinition widgetActionDefinition;

    private boolean aborted;
    private boolean failed;
    private boolean resetRequired;
    private CommandExecutionTask task;

    /**
     * This constructor is called when instantiating the widget action reflectively, all implementations must have
     * this constructor.
     *
     * @param identifier             the identifier for this action, typically corresponds to an {@link AbstractCommand}. Relevant for permissions.
     * @param emojiUnicode           the emoji this action is mapped to that will trigger this action upon a reaction is added.
     * @param resetRequired          whether or not the widget needs to resend its content after this action is done
     * @param context                the {@link CommandContext}
     * @param widget                 the parent widget
     * @param event                  the event that triggered this action
     * @param widgetActionDefinition the {@link WidgetManager.WidgetActionDefinition} consisting of the unique {@link WidgetManager.WidgetActionId}
     *                               and the persistent {@link WidgetContribution.WidgetActionContribution} configuration
     *                               for this action
     */
    protected AbstractWidgetAction(
        String identifier,
        String emojiUnicode,
        boolean resetRequired,
        CommandContext context,
        AbstractWidget widget,
        ButtonInteractionEvent event,
        WidgetManager.WidgetActionDefinition widgetActionDefinition
    ) {
        this.widget = widget;
        this.context = context;
        this.identifier = identifier;
        this.emojiUnicode = emojiUnicode;
        this.resetRequired = resetRequired;
        this.event = event;
        this.widgetActionDefinition = widgetActionDefinition;
    }

    @Override
    public void onSuccess() {
        event.deferEdit().queue();
        if (resetRequired) {
            MessageEmbed newEmbed = widget.reset();
            if (newEmbed != null) {
                event.getHook().editMessageById(widget.getMessage().getId(), MessageEditData.fromEmbeds(newEmbed)).queue();
            } else if (!getWidget().isMessageDeleted()) {
                event.getHook().deleteMessageById(widget.getMessage().getId()).queue();
            }
        }
    }

    @Override
    public void onFailure() {
        event.deferEdit().queue();
    }

    @Override
    public boolean isFailed() {
        return failed;
    }

    @Override
    public void setFailed(boolean failed) {
        this.failed = failed;
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
    public CommandExecutionTask getTask() {
        return task;
    }

    @Override
    public void setTask(CommandExecutionTask task) {
        this.task = task;
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

    @Override
    public PermissionTarget getPermissionTarget() {
        return getWidgetActionDefinition().getId();
    }

    @Override
    public boolean abort() {
        boolean prev = aborted;
        aborted = true;
        return prev;
    }

    @Override
    public boolean isAborted() {
        return aborted;
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

    public WidgetManager.WidgetActionDefinition getWidgetActionDefinition() {
        return widgetActionDefinition;
    }

}
