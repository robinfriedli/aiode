package net.robinfriedli.botify.command.widget.actions;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widget.AbstractWidget;
import net.robinfriedli.botify.command.widget.AbstractWidgetAction;
import net.robinfriedli.botify.command.widget.WidgetManager;

public class RewindAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public RewindAction(String identifier, String emojiUnicode, boolean resetRequired, CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event, WidgetManager.WidgetActionDefinition widgetActionDefinition) {
        super(identifier, emojiUnicode, resetRequired, context, widget, event, widgetActionDefinition);
        audioPlayback = getContext().getGuildContext().getPlayback();
        audioManager = Botify.get().getAudioManager();
    }

    @Override
    public void doRun() {
        AudioQueue queue = audioPlayback.getAudioQueue();
        if (!queue.isEmpty()) {
            if (queue.hasPrevious()) {
                queue.reverse();
            }

            Guild guild = getContext().getGuild();
            GuildVoiceState voiceState = getContext().getMember().getVoiceState();
            audioManager.startPlayback(guild, voiceState != null ? voiceState.getChannel() : null);
        }
    }
}
