package net.robinfriedli.aiode.command.widget.widgets;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.widget.AbstractDecoratingWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.discord.MessageService;

public class QueueWidget extends AbstractDecoratingWidget {

    private final AudioPlayback audioPlayback;

    public QueueWidget(WidgetRegistry widgetRegistry, Guild guild, Message message, AudioPlayback audioPlayback) {
        super(widgetRegistry, guild, message);
        this.audioPlayback = audioPlayback;
    }

    @Override
    public MessageEmbed reset() {
        MessageService messageService = Aiode.get().getMessageService();
        Guild guild = getGuild().get();

        EmbedBuilder embedBuilder = audioPlayback.getAudioQueue().buildMessageEmbed(audioPlayback, guild);
        return messageService.buildEmbed(embedBuilder);
    }

}
