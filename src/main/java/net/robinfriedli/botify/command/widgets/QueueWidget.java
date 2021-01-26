package net.robinfriedli.botify.command.widgets;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.discord.MessageService;

public class QueueWidget extends AbstractWidget {

    private final AudioPlayback audioPlayback;

    public QueueWidget(WidgetManager widgetManager, Message message, AudioPlayback audioPlayback) {
        super(widgetManager, message);
        this.audioPlayback = audioPlayback;
    }

    @Override
    public void reset() {
        MessageService messageService = Botify.get().getMessageService();
        Guild guild = getGuild();
        MessageChannel channel = getChannel();
        Message message = getMessage();

        EmbedBuilder embedBuilder = audioPlayback.getAudioQueue().buildMessageEmbed(audioPlayback, guild);
        MessageEmbed messageEmbed = messageService.buildEmbed(embedBuilder);
        messageService.executeMessageAction(channel, c -> c.editMessageById(message.getId(), messageEmbed), Permission.MESSAGE_EMBED_LINKS);
    }

}
