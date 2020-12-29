package net.robinfriedli.botify.command.widget.actions;

import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widget.AbstractPaginationAction;
import net.robinfriedli.botify.command.widget.AbstractPaginationWidget;
import net.robinfriedli.botify.command.widget.AbstractWidget;
import net.robinfriedli.botify.command.widget.WidgetManager;

public class FirstPageAction extends AbstractPaginationAction {

    public FirstPageAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
    }

    @Override
    public void doRun() throws Exception {
        AbstractPaginationWidget<?> paginationWidget = getPaginationWidget();

        int pageCount = paginationWidget.getPages().size();

        if (pageCount > 1) {
            paginationWidget.setCurrentPage(0);
        } else {
            setFailed(true);
        }
    }
}
