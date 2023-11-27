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

import static com.github.heuermh.seaeagle.Formatting.abbreviate;
import static com.github.heuermh.seaeagle.Formatting.align;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Pretty formatted table.
 */
class PrettyTable {
    private final boolean skipHeaderWhenEmpty;

    private final char junctionChar;
    private final char horizontalChar;
    private final char verticalChar;

    private final List<List<String>> rows;
    private final List<Integer> rowHeights;
    private final List<String> columnNames;
    private final List<Integer> columnWidths;
    private final List<HorizontalAlignment> columnAlignments;
    private int headerHeight;
    private final int leftPad;

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
        this.columnAlignments = new ArrayList<HorizontalAlignment>();
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
            if (entry == null) {
                splitRows.add("");
            }
            else {
                splitRows.addAll(Arrays.asList(entry.split("\n")));
            }
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

    void addColumn(final String columnName, final HorizontalAlignment alignment) {
        List<String> lines = Arrays.asList(columnName.split("\n"));
        int maxWidth = lines.stream().map(String::length).max(Integer::compareTo).orElse(0);

        columnWidths.add(maxWidth);
        columnAlignments.add(alignment);
        columnNames.add(columnName);
        headerHeight = Math.max(lines.size(), headerHeight);
    }
    
    protected List<String> headerLines() {
        List<HorizontalAlignment> alignments = Collections.nCopies(columnNames.size(), HorizontalAlignment.CENTER);
        return formatRow(columnNames, headerHeight, alignments);
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
        return List.of(junctionChar + String.join(String.valueOf(junctionChar), entries) + junctionChar);
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
                                   final List<HorizontalAlignment> columnAlignments) {

        List<String> printedRows = new ArrayList<>();
        Iterator<String> entryIterator = entries.iterator();

        for (int i = 0; i < rowHeight; i++) {
            List<String> cells = new ArrayList<>();
            for (int j = 0; j < entries.size(); j++) {
                String entry = entryIterator.next();
                HorizontalAlignment horizontalAlignment = columnAlignments.get(j);
                int cellWidth = columnWidths.get(j);
                cells.add(formatCell(entry, cellWidth, rowHeight, horizontalAlignment, VerticalAlignment.TOP));
            }
            printedRows.add(verticalChar + String.join(String.valueOf(verticalChar), cells) + verticalChar);
        }
        return printedRows;
    }

    private String formatCell(final String entry,
                              final int cellWidth,
                              final int cellHeight,
                              final HorizontalAlignment horizontalAlignment,
                              final VerticalAlignment verticalAlignment) {

        List<String> entryLines = new ArrayList<>(Arrays.asList(abbreviate(entry, cellWidth).split("\n")));
        if (entryLines.size() > cellHeight) {
            throw new IllegalArgumentException("too many lines (" + entryLines.size() + ") for a cell of size " + cellHeight);
        }

        List<String> alignedLines = align(entryLines, cellHeight, cellWidth, horizontalAlignment, verticalAlignment);
        return String.join("", alignedLines);
    }
}
