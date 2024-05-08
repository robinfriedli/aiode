package net.robinfriedli.aiode.command.widget;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.robinfriedli.aiode.util.EmbedTable;

public abstract class EmbedTablePaginationWidget<E> extends AbstractPaginationWidget<E> {

    public EmbedTablePaginationWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, List<E> elements, int pageSize) {
        super(widgetRegistry, guild, channel, elements, pageSize);
    }

    public EmbedTablePaginationWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel, List<List<E>> pages) {
        super(widgetRegistry, guild, channel, pages);
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
