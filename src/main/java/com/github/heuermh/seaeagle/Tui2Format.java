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

import java.io.IOException;

import java.time.Duration;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.tamboui.layout.Alignment;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Backend;
import dev.tamboui.terminal.BackendFactory;
import dev.tamboui.terminal.Frame;
import dev.tamboui.terminal.Terminal;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitRunner;

import dev.tamboui.tui.TuiConfig;

import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.Row;

import com.google.common.collect.HashBasedTable;

/**
 * Text- or terminal-based UI (tui2) format.
 */
class Tui2Format extends ResultsProcessor {
    private boolean running = true;
    private boolean seenHeader = false;
    private boolean seenHeaderRow = false;
    private List<String> columnNames;
    private List<Alignment> columnAlignments;
    private final TableState tableState = new TableState();
    private final com.google.common.collect.Table<Integer, String, String> tableModel = HashBasedTable.create();

    Tui2Format() {
        // empty
    }

    @Override
    void columns(final List<ColumnInfo> columns) {
        if (!seenHeader) {
            columnAlignments = new ArrayList<>(columns.size());
            columnNames = new ArrayList<>(columns.size());
            for (ColumnInfo columnInfo : columns) {
                columnNames.add(columnInfo.name());
                Alignment columnAlign = "varchar".equals(columnInfo.type()) ? Alignment.LEFT : Alignment.RIGHT;
                columnAlignments.add(columnAlign);
            }
            seenHeader = true;
        }
    }

    // sigh... 
    protected final boolean isHeaderRow(final List<ColumnInfo> columns, final Row row) {
        if (columns.isEmpty()) {
            return false;
        }
        if (row.data().isEmpty()) {
            return false;
        }
        String firstColumnName = columns.get(0).name();
        String firstRowValue = row.data().get(0).varCharValue();

        if (firstColumnName.equals(firstRowValue)) {
            seenHeaderRow = true;
            return true;
        }
        return false;
    }

    @Override
    void rows(final List<ColumnInfo> columns, final List<Row> rows) {
        for (Row row : rows) {
            if (seenHeaderRow || !isHeaderRow(columns, row)) {
                int columnIndex = 0;
                int rowIndex = tableModel.rowKeySet().size();
                for (Datum datum : row.data()) {
                    tableModel.put(rowIndex, columnNames.get(columnIndex), datum.varCharValue());
                    columnIndex++;
                }
            }
        }
    }

    @Override
    void complete() throws IOException {
        // select first row
        tableState.selectFirst();

        var config = TuiConfig.builder()
            .noTick()
            .mouseCapture(false)
            .pollTimeout(Duration.ofMillis(50))
            .resizeGracePeriod(Duration.ofMillis(100))
            .build();

        try (var tui = TuiRunner.create(config)) {
            tui.run((event, runner) -> { return handleEvent(event, runner); }, frame -> renderUI(frame));
        }
        catch (Exception e) {
            throw new IOException("caught " + e.getMessage(), e);
        }
    }

    boolean handleEvent(final Event event, final TuiRunner runner) {
        if (event instanceof KeyEvent) {
            return handleKeyEvent((KeyEvent) event, runner);
        }
        return false;
    }

    boolean handleKeyEvent(final KeyEvent keyEvent, final TuiRunner runner) {
        if (keyEvent.isQuit() || keyEvent.isChar('q') || keyEvent.isKey(KeyCode.ESCAPE)) {
            runner.quit();
            return true;
        }
        else if (keyEvent.isUp() || keyEvent.isChar('k')) {
            tableState.selectPrevious();
            return true;
        }
        else if (keyEvent.isDown() || keyEvent.isChar('j')) {
            tableState.selectNext(tableModel.rowKeySet().size());
            return true;
        }
        else if (keyEvent.isHome() || keyEvent.isChar('g')) {
            tableState.selectFirst();
            return true;
        }
        else if (keyEvent.isEnd() || keyEvent.isChar('G')) {
            tableState.selectLast(tableModel.rowKeySet().size());
            return true;
        }
        // todo: page up, page down
        return false;
    }

    private void renderUI(final Frame frame) {
        Rect area = frame.area();

        List<Rect> layout = Layout.vertical()
            .constraints(
                Constraint.fill(),     // table
                Constraint.length(3)   // footer
            )
            .split(area);

        renderTable(frame, layout.get(0));
        renderFooter(frame, layout.get(1));
    }

    private List<Cell> headerRow() {
        List<Cell> cells = new ArrayList<Cell>();
        for (String columnName : columnNames) {
            cells.add(Cell.from(columnName == null ? "" : columnName).style(Style.EMPTY.bold()).alignment(Alignment.CENTER));
        }
        // add an extra one to the right
        cells.add(Cell.from("").style(Style.EMPTY.bold()).alignment(Alignment.CENTER));
        return cells;
    }

    private List<Cell> dataRow(final int rowKey) {
        List<Cell> rowValues = new ArrayList<Cell>();
        Map<String, String> row = tableModel.row(rowKey);
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            Alignment columnAlignment = columnAlignments.get(i);
            String rowValue = row.get(columnName);
            rowValues.add(Cell.from(rowValue == null ? "" : rowValue).style(Style.EMPTY.notBold()).alignment(columnAlignment));
        }
        // add an extra one to the right
        rowValues.add(Cell.from("").style(Style.EMPTY.notBold()).alignment(Alignment.CENTER));
        return rowValues;
    }

    private List<Constraint> columnWidths() {
        List<Constraint> columnWidths = new ArrayList<Constraint>();
        int distribute = 100 / (columnNames.size() + 1);
        for (int i = 0; i < columnNames.size(); i++) {
            columnWidths.add(Constraint.percentage(distribute));
        }
        // add an extra one to the right
        columnWidths.add(Constraint.fill());
        return columnWidths;
    }

    private void renderTable(final Frame frame, final Rect area) {

        // create header row
        com.github.heuermh.seaeagle.Row header = com.github.heuermh.seaeagle.Row.from(headerRow()).style(Style.EMPTY.fg(Color.YELLOW));

        // create data rows with alternating colors
        List<com.github.heuermh.seaeagle.Row> rows = new ArrayList<>();
        for (int i = 0; i < tableModel.rowKeySet().size(); i++) {
            Style rowStyle = i % 2 == 0 ? Style.EMPTY : Style.EMPTY.bg(Color.indexed(236));
            rows.add(com.github.heuermh.seaeagle.Row.from(dataRow(i)).style(rowStyle));
        }

        Table table = Table.builder()
            .header(header)
            .rows(rows)
            .widths(columnWidths())
            .highlightStyle(Style.EMPTY.bg(Color.BLUE).fg(Color.WHITE).bold())
            .highlightSymbol(" ▶ ")
            .columnSpacing(1)
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.GREEN))
                .title(Title.from(
                    Line.from(
                        Span.raw(" (" + tableModel.rowKeySet().size() + " total) ").dim()
                    )
                ))
                .build())
            .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private void renderFooter(final Frame frame, final Rect area) {
        Line helpLine = Line.from(
            Span.raw(" j/↓").bold().yellow(),
            Span.raw(" Down  ").dim(),
            Span.raw("k/↑").bold().yellow(),
            Span.raw(" Up  ").dim(),
            Span.raw("g").bold().yellow(),
            Span.raw(" First  ").dim(),
            Span.raw("G").bold().yellow(),
            Span.raw(" Last  ").dim(),
            Span.raw("q").bold().yellow(),
            Span.raw(" Quit").dim()
        );

        Paragraph footer = Paragraph.builder()
            .text(Text.from(helpLine))
            .block(Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .build())
            .build();

        frame.renderWidget(footer, area);
    }
}
