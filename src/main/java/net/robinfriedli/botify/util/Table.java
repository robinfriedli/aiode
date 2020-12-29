package net.robinfriedli.botify.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;

/**
 * Utility class for building and printing normalized tables. Necessary since Discord does not support markdown tables.
 * <p>
 * Hint: when sending the table to discord it should be wrapped in "```" markdown highlights to send the table as a code
 * snippet to make sure all characters are the same size.
 * <p>
 * As of botify 1.4 the table class has been replaced by the usage of {@link EmbedBuilder}
 * <p>
 * See {@link EmbedTable} to create tables for the EmbedBuilder (since v1.5.1)
 */
public class Table {

    private final String verticalDelimiter;
    private final String horizontalDelimiter;
    private final String rowDelimiter;
    private final String headDelimiter;

    private final int width;
    private final int padding;
    // add an empty line at the the top and bottom of each row
    private final boolean verticalPadding;
    private final List<Row> rows;
    private Row tableHead;

    public Table() {
        this(50, 2, true);
    }

    public Table(int width, int padding, boolean verticalPadding) {
        this(width, padding, verticalPadding, "|", "_", "-", "=");
    }

    public Table(int width,
                 int padding,
                 boolean verticalPadding,
                 String verticalDelimiter,
                 String horizontalDelimiter,
                 String rowDelimiter,
                 String headDelimiter) {
        if (width < 1) {
            throw new IllegalArgumentException("Width less than 1");
        }

        if (padding < 0) {
            throw new IllegalArgumentException("Padding less than 0");
        }

        this.width = width;
        this.padding = padding;
        this.verticalPadding = verticalPadding;
        rows = Lists.newArrayList();

        this.verticalDelimiter = verticalDelimiter;
        this.horizontalDelimiter = horizontalDelimiter;
        this.rowDelimiter = rowDelimiter;
        this.headDelimiter = headDelimiter;
    }

    public static Table create() {
        return new Table();
    }

    public static Table create(int width, int padding, boolean verticalPadding) {
        return new Table(width, padding, verticalPadding);
    }

    public static Table create(int width,
                               int padding,
                               boolean verticalPadding,
                               String verticalDelimiter,
                               String horizontalDelimiter,
                               String rowDelimiter,
                               String headDelimiter) {
        return new Table(width, padding, verticalPadding, verticalDelimiter, horizontalDelimiter, rowDelimiter, headDelimiter);
    }

    public static Table createNoBorder() {
        return new Table(50, 2, true, "", "", "", "");
    }

    public static Table createNoBorder(int width, int padding, boolean verticalPadding) {
        return new Table(width, padding, verticalPadding, "", "", "", "");
    }

    public String normalize() {
        StringBuilder sb = new StringBuilder();
        sb.append(horizontalDelimiter.repeat(getTableWith())).append(System.lineSeparator());

        if (tableHead != null) {
            sb.append(tableHead.normalize());
            sb.append(verticalDelimiter)
                .append(headDelimiter.repeat(getTableWith() - 2 * verticalDelimiter.length()))
                .append(verticalDelimiter).append(System.lineSeparator());
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            sb.append(row.normalize());
            if (i < rows.size() - 1) {
                sb.append(verticalDelimiter)
                    .append(rowDelimiter.repeat(getTableWith() - 2 * verticalDelimiter.length()))
                    .append(verticalDelimiter).append(System.lineSeparator());
            } else {
                sb.append(verticalDelimiter)
                    .append(horizontalDelimiter.repeat(getTableWith() - 2 * verticalDelimiter.length()))
                    .append(verticalDelimiter).append(System.lineSeparator());
            }
        }

        return sb.toString();
    }

    public Cell createCell(String content) {
        return new Cell(content);
    }

    public Cell createCell(String content, int width) {
        return new Cell(content, width);
    }

    public void setTableHead(Cell... cells) {
        this.tableHead = new Row(Arrays.asList(cells));
    }

    public void addRow(Cell... cells) {
        rows.add(new Row(Arrays.asList(cells)));
    }

    public void addRow(Row row) {
        rows.add(row);
    }

    public int getTableWith() {
        return width;
    }

    public class Row {

        private final List<Cell> cells;
        private final boolean isOverflow;
        private int lines = 1;

        Row(List<Cell> cells) {
            if (cells.size() < 1) {
                throw new IllegalArgumentException("Empty row");
            }

            this.cells = cells;
            defineCellWidths();
            cells.forEach(Cell::handleOverflow);
            isOverflow = cells.stream().anyMatch(Cell::isOverflow);
            if (isOverflow) {
                //noinspection OptionalGetWithoutIsPresent
                lines = cells.stream().filter(Cell::isOverflow).mapToInt(Cell::getLineCount).max().getAsInt();
            }
        }

        String normalize() {
            StringBuilder sb = new StringBuilder();
            if (verticalPadding) {
                writeEmptyLine(sb);
            }

            if (isOverflow) {
                for (int i = 0; i < lines; i++) {
                    sb.append(verticalDelimiter);
                    for (Cell cell : cells) {
                        sb.append(cell.normalize(i)).append(verticalDelimiter);
                    }
                    sb.append(System.lineSeparator());
                }
            } else {
                sb.append(verticalDelimiter);
                for (Cell cell : cells) {
                    sb.append(cell.normalize()).append(verticalDelimiter);
                }
                sb.append(System.lineSeparator());
            }

            if (verticalPadding) {
                writeEmptyLine(sb);
            }
            return sb.toString();
        }

        List<Cell> getCells() {
            return cells;
        }

        private void writeEmptyLine(StringBuilder sb) {
            sb.append(verticalDelimiter);
            for (Cell cell : cells) {
                int cellWidth = cell.getCellWith();
                sb.append(" ".repeat(cellWidth)).append(verticalDelimiter);
            }
            sb.append(System.lineSeparator());
        }

        private void defineCellWidths() {
            int totalCellWidth = cells.stream().filter(cell -> cell.getCellWith() != null).mapToInt(Cell::getCellWith).sum();

            if (totalCellWidth > getTableWith()) {
                throw new IllegalStateException("Total cell width of row larger than table width");
            }

            List<Cell> undefinedCells = cells.stream().filter(cell -> cell.getCellWith() == null).collect(Collectors.toList());
            if (!undefinedCells.isEmpty()) {
                int availableSpace = getTableWith() - totalCellWidth;

                if (availableSpace == 0) {
                    throw new IllegalStateException("No space left for remaining cells");
                }

                int widthPerCell = availableSpace / undefinedCells.size();

                if (widthPerCell < 1) {
                    throw new IllegalStateException("Cell with less than 1");
                }

                undefinedCells.forEach(cell -> cell.setWidth(widthPerCell));
            }

            int finalTotalCellWidth = cells.stream().mapToInt(Cell::getCellWith).sum();
            if (finalTotalCellWidth < getTableWith()) {
                int unusedSpace = getTableWith() - finalTotalCellWidth;
                Cell lastCell = cells.get(cells.size() - 1);
                lastCell.setWidth(lastCell.getCellWith() + unusedSpace);
            }

            // reduce width of cells used by the delimiter.
            Cell firstCell = cells.get(0);
            firstCell.setWidth(firstCell.getCellWith() - verticalDelimiter.length());
            cells.forEach(cell -> cell.setWidth(cell.getCellWith() - verticalDelimiter.length()));
        }
    }

    public class Cell {

        private final String content;
        private final List<String> lines;
        private Integer width;
        private boolean overflow;

        Cell(String content) {
            this.content = content;
            lines = Lists.newArrayList(content);
        }

        Cell(String content, int width) {
            this.content = content;
            lines = Lists.newArrayList(content);
            checkWidth(width);
            this.width = width;
        }

        String normalize() {
            if (isOverflow()) {
                throw new IllegalStateException("Cell is overflown and no line number provided. Use normalize(int line)");
            }

            return doNormalize(content);
        }

        String normalize(int line) {
            if (lines != null && line < lines.size()) {
                return doNormalize(lines.get(line));
            } else {
                return " ".repeat(getCellWith());
            }
        }

        void setWidth(int width) {
            checkWidth(width);
            this.width = width;
        }

        boolean isOverflow() {
            return overflow;
        }

        Integer getCellWith() {
            return width;
        }

        int getLineCount() {
            return isOverflow() ? lines.size() : 1;
        }

        void handleOverflow() {
            if (width == null) {
                throw new IllegalStateException("Width is not defined (yet)");
            }

            int effectiveWidth = content.length() + 2 * padding;
            if (effectiveWidth > width) {
                lines.clear();
                overflow = true;

                int maxContentLength = width - 2 * padding;
                int start = 0;
                int i = 1;
                for (int end = maxContentLength;
                     start != end;
                     start = end, end = i * maxContentLength > content.length() ? content.length() : i * maxContentLength) {
                    lines.add(content.substring(start, end).trim());
                    ++i;
                }
            }
        }

        private String doNormalize(String content) {
            int contentSpace = getCellWith() - 2 * padding;
            int unusedSpace = contentSpace - content.length();
            String paddingString = " ".repeat(padding);
            String fillerString = " ".repeat(unusedSpace);
            return paddingString + content + fillerString + paddingString;
        }

        private void checkWidth(int width) {
            if (width < 1) {
                throw new IllegalArgumentException("Width less than 1");
            }

            if (width > getTableWith()) {
                throw new IllegalArgumentException("Cell width " + width + " is larger than table width " + getTableWith());
            }

            // padding on both sides plus at least one space for content
            if (width < padding * 2 + 1) {
                throw new IllegalArgumentException("Width of cell not large enough to hold padding and content");
            }
        }
    }
}
