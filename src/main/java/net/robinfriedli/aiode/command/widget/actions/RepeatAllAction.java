package net.robinfriedli.aiode.command.widget.actions;

import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.AbstractWidgetAction;
import net.robinfriedli.aiode.command.widget.WidgetManager;

public class RepeatAllAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;

    public RepeatAllAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
        audioPlayback = context.getGuildContext().getPlayback();
    }

    @Override
    public void doRun() {
        audioPlayback.setRepeatAll(!audioPlayback.isRepeatAll());
    }
}
