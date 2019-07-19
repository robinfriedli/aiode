package net.robinfriedli.botify.command.widgets.actions;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.util.EmojiConstants;

public class RewindAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public RewindAction(AudioPlayback audioPlayback, AudioManager audioManager) {
        super("rewind", EmojiConstants.REWIND, true);
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    protected void handleReaction(GuildMessageReactionAddEvent event) {
        AudioQueue queue = audioPlayback.getAudioQueue();
        if (!queue.isEmpty()) {
            if (queue.hasPrevious()) {
                queue.reverse();
            }

            User user = event.getUser();
            Guild guild = event.getGuild();
            audioManager.playTrack(guild, guild.getMember(user).getVoiceState().getChannel());
        }
    }
}
