package net.robinfriedli.aiode.command.widget.actions;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.AbstractWidgetAction;
import net.robinfriedli.aiode.command.widget.WidgetManager;
import net.robinfriedli.aiode.util.EmojiConstants;

/**
 * Action registered on the {@link EmojiConstants#VOLUME_UP} emoji that increases the volume of the currently playing audio.
 */
public class VolumeUpAction extends AbstractWidgetAction {

    public VolumeUpAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, ButtonInteractionEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
    }

    @Override
    public void doRun() {
        AudioPlayback audioPlayback = getContext().getGuildContext().getPlayback();
        audioPlayback.setVolume(audioPlayback.getVolume() + 10);
    }
}
