package net.robinfriedli.aiode.command.widget.widgets;

import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.robinfriedli.aiode.command.widget.AbstractPaginationWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import org.jetbrains.annotations.Nullable;

public class PaginatedMessageEmbedWidget extends AbstractPaginationWidget<MessageEmbed.Field> {

    private final MessageEmbed messageEmbed;

    public PaginatedMessageEmbedWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, MessageEmbed messageEmbed) {
        super(widgetRegistry, guild, channel, messageEmbed.getFields(), 25);
        this.messageEmbed = messageEmbed;
    }

    @Override
    protected String getTitle() {
        return messageEmbed.getTitle();
    }

    @Nullable
    @Override
    protected String getDescription() {
        return messageEmbed.getDescription();
    }

    @Override
    protected void handlePage(EmbedBuilder embedBuilder, List<MessageEmbed.Field> page) {
        for (MessageEmbed.Field field : page) {
            embedBuilder.addField(field);
        }
    }
}
