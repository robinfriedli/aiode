package net.robinfriedli.botify.command.widget;

import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.command.CommandContext;

public abstract class AbstractPaginationAction extends AbstractWidgetAction {

    public AbstractPaginationAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
    }

    protected AbstractPaginationWidget<?> getPaginationWidget() {
        AbstractWidget widget = getWidget();

        if (!(widget instanceof AbstractPaginationWidget)) {
            throw new IllegalStateException(String.format("Action %s can only be used for widgets that implement %s", getClass().getSimpleName(), AbstractPaginationWidget.class));
        }

        return (AbstractPaginationWidget<?>) widget;
    }

}
