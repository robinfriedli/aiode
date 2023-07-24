package net.robinfriedli.aiode.command.widget.actions;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.widget.AbstractPaginationAction;
import net.robinfriedli.aiode.command.widget.AbstractPaginationWidget;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.WidgetManager;

public class LastPageAction extends AbstractPaginationAction {

    public LastPageAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, ButtonInteractionEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
    }

    @Override
    public void doRun() throws Exception {
        AbstractPaginationWidget<?> paginationWidget = getPaginationWidget();

        int pageCount = paginationWidget.getPages().size();
        if (pageCount > 1) {
            paginationWidget.setCurrentPage(pageCount - 1);
        } else {
            setFailed(true);
        }
    }
}
