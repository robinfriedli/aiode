package net.robinfriedli.botify.command.widgets.actions;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.util.EmojiConstants;

public class SkipAction extends AbstractWidgetAction {

    private final AudioPlayback audioPlayback;
    private final AudioManager audioManager;

    public SkipAction(AudioPlayback audioPlayback, AudioManager audioManager) {
        super("skip", EmojiConstants.SKIP, true);
        this.audioPlayback = audioPlayback;
        this.audioManager = audioManager;
    }

    @Override
    protected void handleReaction(GuildMessageReactionAddEvent event) {
        AudioQueue queue = audioPlayback.getAudioQueue();
        if (!queue.isEmpty()) {
            User user = event.getUser();
            Guild guild = event.getGuild();
            if (queue.hasNext()) {
                queue.iterate();
                audioManager.playTrack(guild, guild.getMember(user).getVoiceState().getChannel());
            } else {
                audioPlayback.stop();
            }
        }
    }
}
