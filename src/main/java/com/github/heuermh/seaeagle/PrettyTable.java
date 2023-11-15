/*
 * The authors of this file license it to you under the
 * Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You
 * may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.heuermh.seaeagle;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class PrettyTable {
    private boolean skipHeaderWhenEmpty;

    private char junctionChar;
    private char horizontalChar;
    private char verticalChar;

    private List<List<String>> rows;
    private List<Integer> rowHeights;
    private List<String> columnNames;
    private List<Integer> columnWidths;
    private List<String> columnAlignments;
    private int headerHeight;
    private int leftPad;

    PrettyTable(final boolean skipHeaderWhenEmpty,
                final char junctionChar,
                final char horizontalChar,
                final char verticalChar,
                final int leftPad) {

        this.skipHeaderWhenEmpty = skipHeaderWhenEmpty;
        this.junctionChar = junctionChar;
        this.horizontalChar = horizontalChar;
        this.verticalChar = verticalChar;
        this.rows = new ArrayList<>();
        this.rowHeights = new ArrayList<>();
        this.columnNames = new ArrayList<>();
        this.columnWidths = new ArrayList<>();
        this.columnAlignments = new ArrayList<>();
        this.headerHeight = 1;
        this.leftPad = leftPad;
    }

    @Override
    public String toString() {
        if (rows.size() > 0 || !skipHeaderWhenEmpty) {
            List<String> lines = new ArrayList<>();
            lines.add("");
            lines.addAll(formatHeader());
            lines.addAll(formatRows());
            lines.addAll(formatFooter());
            lines.add("");
            return String.join("\n" + " ".repeat(leftPad), lines);
        } else {
            return "";
        }
    }

    void addRow(final List<String> row) {
        if (row.size() != columnNames.size()) {
            throw new IllegalArgumentException("invalid row length: " + row.size());
        }

        List<String> splitRows = new ArrayList<>();
        for (String entry : row) {
            splitRows.addAll(Arrays.asList(entry.split("\n")));
        }
        
        // ?? this is the max width after splitting by \n
        int maxHeight = splitRows.stream().map(String::length).max(Integer::compareTo).orElse(0);
        //rowHeights.add(maxHeight);
        rowHeights.add(1);

        for (int i = 0; i < splitRows.size(); i++) {
            String entry = splitRows.get(i);
            int entryLength = entry.length();
            int columnWidth = columnWidths.get(i);
            columnWidths.set(i, Math.max(entryLength, columnWidth));
        }

        rows.add(row);
    }

    void addColumn(final String columnName, final String align) {
        // todo: horizontal and vertical alignment enums
        if (!Arrays.asList('l', 'c', 'r').contains(align.charAt(0))) {
            throw new IllegalArgumentException("invalid column alignment: " + align);
        }

        List<String> lines = Arrays.asList(columnName.split("\n"));
        int maxWidth = lines.stream().map(String::length).max(Integer::compareTo).orElse(0);

        columnWidths.add(maxWidth);
        columnAlignments.add(align);
        columnNames.add(columnName);
        headerHeight = Math.max(lines.size(), headerHeight);
    }
    
    protected List<String> headerLines() {
        List<String> aligns = Collections.nCopies(columnNames.size(), "c");
        return formatRow(columnNames, headerHeight, aligns);
    }

    protected List<String> formatHeader() {
        List<String> header = new ArrayList<>();
        header.addAll(formatHrule());
        header.addAll(headerLines());
        header.addAll(formatHrule());
        return header;
    }

    protected List<String> formatHrule() {
        List<String> entries = new ArrayList<>();
        for (int width : columnWidths) {
            entries.add(String.valueOf(horizontalChar).repeat(width + 2));
        }
        return Arrays.asList(junctionChar + String.join(String.valueOf(junctionChar), entries) + junctionChar);
    }

    protected List<String> formatRows() {
        List<String> result = new ArrayList<>();
        for (List<String> row : rows) {
            result.addAll(formatRow(row, rowHeights.get(rows.indexOf(row)), columnAlignments));
        }
        return result;
    }

    protected List<String> formatFooter() {
        return formatHrule();
    }

    private List<String> formatRow(final List<String> entries,
                                   final int rowHeight,
                                   final List<String> columnAlignments) {

        List<String> printedRows = new ArrayList<>();
        Iterator<String> entryIterator = entries.iterator();

        for (int i = 0; i < rowHeight; i++) {
            List<String> cells = new ArrayList<>();
            for (int j = 0; j < entries.size(); j++) {
                String entry = entryIterator.next();
                String align = columnAlignments.get(j);
                int cellWidth = columnWidths.get(j);
                // todo: specify valign
                cells.add(formatCell(entry, cellWidth, rowHeight, align, "t"));
            }
            printedRows.add(verticalChar + String.join(String.valueOf(verticalChar), cells) + verticalChar);
        }

        return printedRows;
    }

    private String formatCell(final String entry,
                              final int cellWidth,
                              final int cellHeight,
                              final String align,
                              final String valign) {

        List<String> entryLines = new ArrayList<>(Arrays.asList(abbreviate(entry, cellWidth).split("\n")));

        if (entryLines.size() > cellHeight) {
            throw new IllegalArgumentException("too many lines (" + entryLines.size() + ") for a cell of size " + cellHeight);
        }

        List<String> topLines = new ArrayList<>();
        List<String> bottomLines = new ArrayList<>();

        if (valign.equals("t")) {
            bottomLines = Collections.nCopies(cellHeight - entryLines.size(), " ".repeat(cellWidth + 2));
        }
        else if (valign.equals("c")) {
            int[] paddings = centeredPadding(cellHeight, entryLines.size());
            topLines = Collections.nCopies(paddings[0], " ".repeat(cellWidth + 2));
            bottomLines = Collections.nCopies(paddings[1], " ".repeat(cellWidth + 2));
        }
        else if (valign.equals("b")) {
            topLines = Collections.nCopies(cellHeight - entryLines.size(), " ".repeat(cellWidth + 2));
        }
        else {
            throw new IllegalArgumentException("unknown value for valign: " + align);
        }

        List<String> contentLines = new ArrayList<>();
        for (String line : entryLines) {
            if (align.equals("c")) {
                int[] paddings = centeredPadding(cellWidth, line.length());
                contentLines.add(" " + " ".repeat(paddings[0]) + line + " ".repeat(paddings[1]) + " ");
            }
            else if (align.equals("l") || align.equals("r")) {
                String fmt = (align.equals("l") ? " %-" : " %") + cellWidth + "s ";
                contentLines.add(String.format(fmt, line));
            }
            else {
                throw new IllegalArgumentException("unknown alignment: " + align);
            }
        }

        List<String> result = new ArrayList<>(topLines);
        result.addAll(contentLines);
        result.addAll(bottomLines);

        return String.join("", result);
    }

    private int[] centeredPadding(final int interval, final int size) {
        if (size > interval) {
            throw new IllegalArgumentException("size " + size + " must be less than or equal to interval " + interval);
        }

        boolean sameParity = (interval % 2) == (size % 2);
        int padding = (interval - size) / 2;

        if (sameParity) {
            return new int[]{ padding, padding };
        } else {
            return new int[]{ padding, padding + 1 };
        }
    }

    private String abbreviate(final String s, final int width) {
        String suffix = ".".repeat(Math.min(width, 3));
        return s.length() <= width ? s : s.substring(0, width - suffix.length()) + suffix;
    }
}
