package net.robinfriedli.botify.command.widget.actions;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widget.AbstractWidget;
import net.robinfriedli.botify.command.widget.AbstractWidgetAction;
import net.robinfriedli.botify.command.widget.WidgetManager;
import net.robinfriedli.botify.util.EmojiConstants;

/**
 * Action registered on the {@link EmojiConstants#PLAY_PAUSE} emoji that plays or pauses the current track in the queue
 */
public class PlayPauseAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public PlayPauseAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
        audioPlayback = getContext().getGuildContext().getPlayback();
        audioManager = Botify.get().getAudioManager();
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
