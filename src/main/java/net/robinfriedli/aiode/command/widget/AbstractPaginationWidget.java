package net.robinfriedli.aiode.command.widget;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.discord.MessageService;

/**
 * Abstract widget implementation that enables simple pagination of large lists of elements. Create a specific implementation
 * that defines the columns and add the pagination actions inheriting from {@link AbstractPaginationAction} to the widget
 * configuration to add controls such as iterating to the next page.
 *
 * @param <E>
 */
public abstract class AbstractPaginationWidget<E> extends AbstractWidget {

    protected volatile List<List<E>> pages;

    private int currentPage;

    public AbstractPaginationWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, List<E> elements, int pageSize) {
        this(widgetRegistry, guild, channel, Lists.partition(elements, pageSize));
    }

    public AbstractPaginationWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, List<List<E>> pages) {
        super(widgetRegistry, guild, channel);
        this.pages = pages;
    }

    @Override
    public CompletableFuture<Message> prepareInitialMessage() {
        EmbedBuilder embedBuilder = prepareEmbedBuilderForPage();
        MessageService messageService = Aiode.get().getMessageService();
        return messageService.send(embedBuilder, getChannel().get());
    }

    @Override
    public MessageEmbed reset() {
        MessageService messageService = Aiode.get().getMessageService();

        EmbedBuilder embedBuilder = prepareEmbedBuilderForPage();

        return messageService.buildEmbed(embedBuilder);
    }

    public List<List<E>> getPages() {
        return pages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int incrementPage() {
        return ++currentPage;
    }

    public int decrementPage() {
        return --currentPage;
    }

    protected abstract String getTitle();

    @Nullable
    protected abstract String getDescription();

    /**
     * Defines the implementation of how the elements from the current page are added to the embed message
     */
    protected abstract void handlePage(EmbedBuilder embedBuilder, List<E> page);

    @Override
    public void handleButtonInteraction(ButtonInteractionEvent event, CommandContext context) {
        synchronized (this) {
            super.handleButtonInteraction(event, context);
        }
    }

    protected EmbedBuilder prepareEmbedBuilder() {
        return new EmbedBuilder();
    }

    private EmbedBuilder prepareEmbedBuilderForPage() {
        int pageCount = getPages().size();
        if (currentPage >= pageCount && !getPages().isEmpty()) {
            throw new IllegalStateException(String.format("Current page is out of bounds. Current index: %d; page count: %d", currentPage, pageCount));
        }

        EmbedBuilder embedBuilder = prepareEmbedBuilder();
        String title = getTitle();
        if (!Strings.isNullOrEmpty(title)) {
            embedBuilder.setTitle(title);
        }

        String description = getDescription();
        if (!Strings.isNullOrEmpty(description)) {
            embedBuilder.setDescription(description);
        }

        Aiode aiode = Aiode.get();
        SpringPropertiesConfig springPropertiesConfig = aiode.getSpringPropertiesConfig();
        String baseUri = springPropertiesConfig.requireApplicationProperty("aiode.server.base_uri");
        String logoUrl = baseUri + "/resources-public/img/aiode-logo-small.png";

        embedBuilder.setFooter(String.format("Page %d of %d", currentPage + 1, Math.max(pageCount, 1)), logoUrl);

        List<E> page = getPages().isEmpty() ? Collections.emptyList() : getPages().get(currentPage);
        handlePage(embedBuilder, page);
        return embedBuilder;
    }

}
