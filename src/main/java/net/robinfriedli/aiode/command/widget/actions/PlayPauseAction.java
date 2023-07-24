package net.robinfriedli.aiode.command.widget.actions;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.widget.AbstractWidget;
import net.robinfriedli.aiode.command.widget.AbstractWidgetAction;
import net.robinfriedli.aiode.command.widget.WidgetManager;
import net.robinfriedli.aiode.util.EmojiConstants;

/**
 * Action registered on the {@link EmojiConstants#PLAY_PAUSE} emoji that plays or pauses the current track in the queue
 */
public class PlayPauseAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public PlayPauseAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, ButtonInteractionEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
        audioPlayback = getContext().getGuildContext().getPlayback();
        audioManager = Aiode.get().getAudioManager();
    }

    @Override
    public void doRun() {
        if (audioPlayback.isPlaying()) {
            audioPlayback.pause();
        } else if (!audioPlayback.getAudioQueue().isEmpty() || audioPlayback.isPaused()) {
            Guild guild = getContext().getGuild();
            GuildVoiceState voiceState = getContext().getMember().getVoiceState();
            audioManager.startOrResumePlayback(guild, voiceState != null ? voiceState.getChannel() : null);
        } else {
            setResetRequired(false);
        }
    }
}
