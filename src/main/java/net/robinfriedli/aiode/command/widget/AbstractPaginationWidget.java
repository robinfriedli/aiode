package net.robinfriedli.aiode.command.widget;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
import net.robinfriedli.aiode.util.EmbedTable;

/**
 * Abstract widget implementation that enables simple pagination of large lists of elements. Create a specific implementation
 * that defines the columns and add the pagination actions inheriting from {@link AbstractPaginationAction} to the widget
 * configuration to add controls such as iterating to the next page.
 *
 * @param <E>
 */
public abstract class AbstractPaginationWidget<E> extends AbstractWidget {

    private final List<List<E>> pages;

    private int currentPage;

    public AbstractPaginationWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, List<E> elements, int pageSize) {
        super(widgetRegistry, guild, channel);
        this.pages = Lists.partition(elements, pageSize);
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

    private EmbedBuilder prepareEmbedBuilderForPage() {
        int pageCount = pages.size();
        if (currentPage >= pageCount && !pages.isEmpty()) {
            throw new IllegalStateException(String.format("Current page is out of bounds. Current index: %d; page count: %d", currentPage, pageCount));
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(getTitle());

        String description = getDescription();
        if (!Strings.isNullOrEmpty(description)) {
            embedBuilder.setDescription(description);
        }

        Aiode aiode = Aiode.get();
        SpringPropertiesConfig springPropertiesConfig = aiode.getSpringPropertiesConfig();
        String baseUri = springPropertiesConfig.requireApplicationProperty("aiode.server.base_uri");
        String logoUrl = baseUri + "/resources-public/img/aiode-logo-small.png";

        embedBuilder.setFooter(String.format("Page %d of %d", currentPage + 1, Math.max(pageCount, 1)), logoUrl);

        List<E> page = pages.isEmpty() ? Collections.emptyList() : pages.get(currentPage);
        handlePage(embedBuilder, page);
        return embedBuilder;
    }

    public abstract static class EmbedTablePaginationWidget<E> extends AbstractPaginationWidget<E> {

        public EmbedTablePaginationWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, List<E> elements, int pageSize) {
            super(widgetRegistry, guild, channel, elements, pageSize);
        }

        protected abstract Column<E>[] getColumns();

        @Override
        protected void handlePage(EmbedBuilder embedBuilder, List<E> page) {
            EmbedTable embedTable = new EmbedTable(embedBuilder);
            for (Column<E> column : getColumns()) {
                Function<E, EmbedTable.Group> groupFunction = column.getGroupFunction();
                if (groupFunction != null) {
                    embedTable.addColumn(column.getTitle(), page, column.getDisplayFunc(), groupFunction);
                } else {
                    embedTable.addColumn(column.getTitle(), page, column.getDisplayFunc());
                }
            }
            embedTable.build();
        }

        public static class Column<E> {

            private final String title;
            private final Function<E, String> displayFunc;
            @Nullable
            private final Function<E, EmbedTable.Group> groupFunction;

            public Column(String title, Function<E, String> displayFunc) {
                this(title, displayFunc, null);
            }

            public Column(String title, Function<E, String> displayFunc, @Nullable Function<E, EmbedTable.Group> groupFunction) {
                this.title = title;
                this.displayFunc = displayFunc;
                this.groupFunction = groupFunction;
            }

            public String getTitle() {
                return title;
            }

            public Function<E, String> getDisplayFunc() {
                return displayFunc;
            }

            @Nullable
            public Function<E, EmbedTable.Group> getGroupFunction() {
                return groupFunction;
            }
        }
    }

}
