package net.robinfriedli.aiode.command.widget;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class DynamicEmbedTablePaginationWidget<E> extends EmbedTablePaginationWidget<E> {

    private final String title;
    @Nullable
    private final String description;
    private final Column<E>[] columns;
    private final List<E> elements;

    public DynamicEmbedTablePaginationWidget(
        WidgetRegistry widgetRegistry,
        Guild guild,
        MessageChannel channel,
        String title,
        @Nullable String description,
        Column<E>[] columns,
        List<E> elements
    ) {
        super(widgetRegistry, guild, channel, null);
        this.title = title;
        this.description = description;
        this.columns = columns;
        this.elements = elements;
    }

    @Override
    public List<List<E>> getPages() {
        if (this.pages == null) {
            synchronized (this) {
                if (this.pages != null) {
                    return this.pages;
                }

                List<List<E>> pages = Lists.newArrayList();
                pages.add(Lists.newArrayList());
                int[] columnLengths = new int[columns.length];
                for (E element : elements) {
                    boolean columnOverflowed = false;
                    int[] elementColLengths = new int[columns.length];
                    for (int colIdx = 0; colIdx < columns.length; colIdx++) {
                        Column<E> column = columns[colIdx];
                        String columnValue = column.getDisplayFunc().apply(element);
                        elementColLengths[colIdx] = columnValue.length();
                        // the current length of the colum plus the field to be added, including all newline characters (1 per field)
                        int currentPageSize = pages.getLast().size();
                        int colLenWithEl = columnValue.length() + columnLengths[colIdx] + currentPageSize;
                        // do not exceed embed field limit of 1000 characters, also limit to 25 items per page
                        if (colLenWithEl >= 1000 || currentPageSize == 25) {
                            if (!columnOverflowed) {
                                pages.add(Lists.newArrayList(element));
                                columnOverflowed = true;
                            }
                        }
                    }
                    if (columnOverflowed) {
                        columnLengths = elementColLengths;
                    } else {
                        pages.getLast().add(element);
                        for (int colIdx = 0; colIdx < columns.length; colIdx++) {
                            columnLengths[colIdx] += elementColLengths[colIdx];
                        }
                    }
                }
                this.pages = pages;
            }
        }
        return this.pages;
    }

    @Override
    protected Column<E>[] getColumns() {
        return columns;
    }

    @Override
    protected String getTitle() {
        return title;
    }

    @Nullable
    @Override
    protected String getDescription() {
        return description;
    }
}
