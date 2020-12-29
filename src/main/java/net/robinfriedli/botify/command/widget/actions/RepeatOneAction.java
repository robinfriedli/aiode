package net.robinfriedli.botify.command.widget.actions;

import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widget.AbstractWidget;
import net.robinfriedli.botify.command.widget.AbstractWidgetAction;
import net.robinfriedli.botify.command.widget.WidgetManager;

public class RepeatOneAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;

    public RepeatOneAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
        audioPlayback = context.getGuildContext().getPlayback();
    }

    @Override
    public void doRun() {
        audioPlayback.setRepeatOne(!audioPlayback.isRepeatOne());
    }
}
