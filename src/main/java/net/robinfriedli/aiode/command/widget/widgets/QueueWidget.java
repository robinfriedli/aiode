package net.robinfriedli.aiode.command.widget.widgets;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.widget.AbstractDecoratingWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.discord.DiscordEntity;
import net.robinfriedli.aiode.discord.MessageService;

public class QueueWidget extends AbstractDecoratingWidget {

    private final AudioPlayback audioPlayback;

    public QueueWidget(WidgetRegistry widgetRegistry, Guild guild, Message message, AudioPlayback audioPlayback) {
        super(widgetRegistry, guild, message);
        this.audioPlayback = audioPlayback;
    }

    @Override
    public void reset() {
        MessageService messageService = Aiode.get().getMessageService();
        Guild guild = getGuild().get();
        MessageChannel channel = getChannel().get();
        DiscordEntity.Message message = getMessage();

        EmbedBuilder embedBuilder = audioPlayback.getAudioQueue().buildMessageEmbed(audioPlayback, guild);
        MessageEmbed messageEmbed = messageService.buildEmbed(embedBuilder);
        messageService.executeMessageAction(channel, c -> c.editMessageById(message.getId(), MessageEditData.fromEmbeds(messageEmbed)), Permission.MESSAGE_EMBED_LINKS);
    }

}
