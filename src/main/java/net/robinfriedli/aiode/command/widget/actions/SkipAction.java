package net.robinfriedli.aiode.command.widget.actions;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.AbstractWidgetAction;
import net.robinfriedli.aiode.command.widget.WidgetManager;

public class SkipAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public SkipAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, ButtonInteractionEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
        audioPlayback = getContext().getGuildContext().getPlayback();
        audioManager = Aiode.get().getAudioManager();
    }

    @Override
    public void doRun() {
        AudioQueue queue = audioPlayback.getAudioQueue();
        if (!queue.isEmpty()) {
            Guild guild = getContext().getGuild();
            if (queue.hasNext()) {
                queue.iterate();
                GuildVoiceState voiceState = getContext().getMember().getVoiceState();
                audioManager.startPlayback(guild, voiceState != null ? voiceState.getChannel() : null);
            } else {
                audioPlayback.stop();
                queue.reset();
            }
        }
    }
}
