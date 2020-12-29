package net.robinfriedli.botify.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.command.widget.AbstractPaginationWidget;

/**
 * Utility class for building tables with embed messages. This class provides rudimentary assistance with adding elements
 * to embed fields, automatically creating additional embed fields for one column if the size becomes too large, however it
 * is advised to use proper pagination for large lists (see {@link AbstractPaginationWidget}). This class does not guarantee
 * that the order of elements is consistent between columns. It is up to the user to make sure they are adding the desired
 * elements to all columns properly and managing grouping orderings correctly (i.e. using the same ordering for all columns).
 */
@SuppressWarnings("unchecked")
public class EmbedTable {

    private final EmbedBuilder embedBuilder;
    private final List<Column> columns;

    public EmbedTable(EmbedBuilder embedBuilder) {
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

    public <E> void addColumn(String title, Collection<E> elements, Function<E, String> displayFunc, Function<E, Group> groupingFunc) {
        Map<Group, List<String>> groupedFields = new HashMap<>();
        for (E element : elements) {
            String field = displayFunc.apply(element);
            Group group = groupingFunc.apply(element);

            List<String> fields = groupedFields.computeIfAbsent(group, g -> new ArrayList<>());
            fields.add(field);
        }

        List<String> fields = new ArrayList<>(elements.size());

        //noinspection unchecked
        groupedFields.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().getOrderBy()))
            .forEach(e -> {
                @SuppressWarnings("unchecked")
                Map.Entry<Group, List<String>> entry = (Map.Entry<Group, List<String>>) e;
                fields.add(entry.getKey().display());
                fields.addAll(entry.getValue());
            });

        columns.add(new Column(title, fields));
    }

    @SuppressWarnings("rawtypes")
    public static class Group {

        private final String name;
        private final boolean displayName;
        private final Object groupingObj;
        private final Comparable orderBy;

        public Group(String name, Object groupingObj) {
            this(name, groupingObj, name);
        }

        public Group(String name, Object groupingObj, Comparable orderBy) {
            this(name, true, groupingObj, orderBy);
        }

        public Group(String name, boolean displayName, Object groupingObj, Comparable orderBy) {
            this.name = name;
            this.displayName = displayName;
            this.groupingObj = groupingObj;
            this.orderBy = orderBy;
        }

        public static Group namedGroup(String name, Object groupingObj) {
            return new Group(name, groupingObj);
        }

        /**
         * New group with a custom ordering field that will be used to order the group amongst the other groups, the ordering
         * does NOT order the items within the group
         */
        public static Group namedGroupOrdered(String name, Object groupingObj, Comparable orderBy) {
            return new Group(name, groupingObj, orderBy);
        }

        public static Group silentGroup(String name, Object groupingObj) {
            return new Group(name, false, groupingObj, name);
        }

        public static Group silentGroupOrdered(String name, Object groupingObj, Comparable orderBy) {
            return new Group(name, false, groupingObj, orderBy);
        }

        public String display() {
            if (displayName) {
                return getName();
            } else {
                return "-";
            }
        }

        public String getName() {
            return name;
        }

        public Object getGroupingObj() {
            return groupingObj;
        }

        @Override
        public int hashCode() {
            return groupingObj.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Group)) {
                return false;
            }
            return Objects.equals(groupingObj, ((Group) obj).getGroupingObj());
        }

        public Comparable getOrderBy() {
            return orderBy;
        }
    }

    public static class Column {

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
