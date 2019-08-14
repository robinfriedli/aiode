package net.robinfriedli.botify.util;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;

/**
 * next generation table for embed messages
 */
public class Table2 {

    private final EmbedBuilder embedBuilder;
    private final List<Column> columns;

    public Table2(EmbedBuilder embedBuilder) {
        this.embedBuilder = embedBuilder;
        columns = Lists.newArrayList();
    }

    public void build() {
        OptionalInt partRowsOptional = columns.stream().mapToInt(Column::partsCount).max();

        if (partRowsOptional.isEmpty()) {
            throw new IllegalStateException("No rows");
        }

        int partRows = partRowsOptional.getAsInt();
        for (int i = 0; i < partRows; i++) {
            for (Column column : columns) {
                embedBuilder.addField(i == 0 ? column.getTitle() : "", column.getPart(i), true);
            }

            if (i < partRows - 1) {
                embedBuilder.addBlankField(false);
            }
        }
    }

    public <E> void addColumn(String title, Collection<E> elements, Function<E, String> displayFunc) {
        columns.add(new Column(title, elements.stream().map(displayFunc).collect(Collectors.toList())));
    }

    public class Column {

        private final String title;
        private final List<String> fields;
        private final List<StringBuilder> parts;

        public Column(String title, List<String> fields) {
            this.title = title;
            this.fields = fields;

            parts = Lists.newArrayList(new StringBuilder());
            for (String field : fields) {
                StringBuilder currentPart = parts.get(parts.size() - 1);
                if (currentPart.length() + field.length() < 1000) {
                    currentPart.append(field).append(System.lineSeparator());
                } else {
                    parts.add(new StringBuilder().append(field).append(System.lineSeparator()));
                }
            }
        }

        public String getTitle() {
            return title;
        }

        public List<String> getFields() {
            return fields;
        }

        public List<StringBuilder> getParts() {
            return parts;
        }

        public int partsCount() {
            return parts.size();
        }

        public String getPart(int i) {
            if (i >= parts.size()) {
                return "";
            } else {
                return parts.get(i).toString();
            }
        }
    }

}
