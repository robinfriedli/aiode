package net.robinfriedli.botify.command.widget.widgets;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.widget.AbstractDecoratingWidget;
import net.robinfriedli.botify.command.widget.WidgetRegistry;
import net.robinfriedli.botify.discord.DiscordEntity;
import net.robinfriedli.botify.discord.MessageService;

public class QueueWidget extends AbstractDecoratingWidget {

    private final AudioPlayback audioPlayback;

    public QueueWidget(WidgetRegistry widgetRegistry, Guild guild, Message message, AudioPlayback audioPlayback) {
        super(widgetRegistry, guild, message);
        this.audioPlayback = audioPlayback;
    }

    @Override
    public void reset() {
        MessageService messageService = Botify.get().getMessageService();
        Guild guild = getGuild().get();
        MessageChannel channel = getChannel().get();
        DiscordEntity.Message message = getMessage();

        EmbedBuilder embedBuilder = audioPlayback.getAudioQueue().buildMessageEmbed(audioPlayback, guild);
        MessageEmbed messageEmbed = messageService.buildEmbed(embedBuilder);
        messageService.executeMessageAction(channel, c -> c.editMessageById(message.getId(), messageEmbed));
    }

}
