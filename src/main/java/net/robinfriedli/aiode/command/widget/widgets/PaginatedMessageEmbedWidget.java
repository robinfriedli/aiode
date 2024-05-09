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

    private final EmbedBuilder embedBuilder;

    public PaginatedMessageEmbedWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, EmbedBuilder embedBuilder) {
        super(widgetRegistry, guild, channel, embedBuilder.getFields(), 25);
        this.embedBuilder = embedBuilder;
    }


    // title and description provided by prepareEmbedBuilder()

    @Override
    protected String getTitle() {
        return "";
    }

    @Nullable
    @Override
    protected String getDescription() {
        return null;
    }

    @Override
    protected void handlePage(EmbedBuilder embedBuilder, List<MessageEmbed.Field> page) {
        for (MessageEmbed.Field field : page) {
            embedBuilder.addField(field);
        }
    }

    @Override
    protected EmbedBuilder prepareEmbedBuilder() {
        EmbedBuilder builderForPage = new EmbedBuilder();
        builderForPage.copyFrom(embedBuilder);
        builderForPage.clearFields();
        return builderForPage;
    }
}
