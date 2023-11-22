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

import static com.github.heuermh.seaeagle.Formatting.align;

import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;

import com.googlecode.lanterna.graphics.ThemeDefinition;

import com.googlecode.lanterna.gui2.AsynchronousTextGUIThread;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

import com.googlecode.lanterna.gui2.table.DefaultTableCellRenderer;
import com.googlecode.lanterna.gui2.table.DefaultTableHeaderRenderer;
import com.googlecode.lanterna.gui2.table.Table;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;

import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.Row;

/**
 * Text- or terminal-based UI (tui) format.
 */
class TuiFormat extends ResultsProcessor {
    private boolean seenHeader = false;
    private boolean seenHeaderRow = false;
    private Table<String> table;
    private List<HorizontalAlignment> columnAlignments;

    TuiFormat() {
        // empty
    }

    @Override
    void columns(final List<ColumnInfo> columns) throws IOException {
        if (!seenHeader) {
            columnAlignments = new ArrayList<>(columns.size());
            List<String> columnNames = new ArrayList<>(columns.size());
            for (ColumnInfo columnInfo : columns) {
                columnNames.add(columnInfo.name());
                HorizontalAlignment columnAlign = "varchar".equals(columnInfo.type()) ? HorizontalAlignment.LEFT : HorizontalAlignment.RIGHT;
                columnAlignments.add(columnAlign);
            }
            table = new Table<String>(columnNames.toArray(new String[0]));
            table.setTableCellRenderer(new PrettyTableCellRenderer());
            table.setTableHeaderRenderer(new PrettyTableHeaderRenderer());

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
    void rows(final List<ColumnInfo> columns, final List<Row> rows) throws IOException {
        for (Row row : rows) {
            if (seenHeaderRow || !isHeaderRow(columns, row)) {
                List<String> rowValues = new ArrayList<>(row.data().size());
                for (Datum datum : row.data()) {
                    rowValues.add(datum.varCharValue());
                }
                table.getTableModel().addRow(rowValues);
            }
        }
    }

    @Override
    void complete() throws IOException {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();

        WindowBasedTextGUI gui = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), screen);
        BasicWindow window = new BasicWindow("sea-eagle");
        // todo: Q and ESC should kill window
        //window.waitUntilClosed();

        // todo: ENTER/RETURN should copy selected row?
        // todo: terminal copy/paste should work on selected row

        window.setHints(Arrays.asList(Window.Hint.NO_DECORATIONS, Window.Hint.FULL_SCREEN, Window.Hint.FIT_TERMINAL_WINDOW));

        Panel panel = new Panel();
        panel.setLayoutManager(new BorderLayout());

        EmptySpace top = new EmptySpace(new TerminalSize(0, 1));
        top.setLayoutData(BorderLayout.Location.TOP);
        panel.addComponent(top);

        EmptySpace left = new EmptySpace(new TerminalSize(4, 0));
        left.setLayoutData(BorderLayout.Location.LEFT);
        panel.addComponent(left);

        table.setLayoutData(BorderLayout.Location.CENTER);
        panel.addComponent(table);

        window.setComponent(panel);
        gui.addWindow(window);

        try {
            AsynchronousTextGUIThread guiThread = (AsynchronousTextGUIThread) gui.getGUIThread();
            guiThread.start();
            guiThread.waitForStop();
        }
        catch (InterruptedException e) {
            // ignore
        }
        finally {
            screen.stopScreen();
        }
    }

    /**
     * Pretty table cell renderer.
     */
    class PrettyTableCellRenderer extends DefaultTableCellRenderer<String> {
        private int padding = 2;

        @Override
        public TerminalSize getPreferredSize(final Table<String> table,
                                             final String cell,
                                             final int columnIndex,
                                             final int rowIndex) {
            TerminalSize preferred =  super.getPreferredSize(table, cell, columnIndex, rowIndex);
            TerminalSize padded = preferred.withRelativeColumns(padding * 2);
            return padded;
        }

        /*
          left and center alignment do not work
        @Override
        protected void render(final Table<String> table,
                              final String cell,
                              final int columnIndex,
                              final int rowIndex,
                              final boolean isSelected,
                              final TextGUIGraphics textGUIGraphics) {

            List<String> lines = Arrays.asList(getContent(cell));
            int columnWidth = getPreferredSize(table, cell, columnIndex, rowIndex).getColumns();

            List<String> alignedLines = align(lines, 1, columnWidth, columnAlignments.get(columnIndex), VerticalAlignment.TOP);

            int rowCount = 0;
            for (String line : alignedLines) {
                textGUIGraphics.putString(0, rowCount++, line);
            }
        }
        */
    }

    /**
     * Pretty table header renderer.
     */
    class PrettyTableHeaderRenderer extends DefaultTableHeaderRenderer<String> {
        private int padding = 2;

        @Override
        public TerminalSize getPreferredSize(final Table<String> table,
                                             final String label,
                                             final int columnIndex) {

            /*
              thread deadlock?
            int maxWidth = 0;
            for (int i = 0; i < table.getTableModel().getRowCount(); i++) {
                int length = TerminalTextUtils.getColumnWidth(table.getTableModel().getCell(i, columnIndex));
                if (maxWidth < length) {
                    maxWidth = length;
                }
            }
            TerminalSize preferred = new TerminalSize(maxWidth, 1);
            */
            TerminalSize preferred = super.getPreferredSize(table, label, columnIndex);
            TerminalSize padded = preferred.withRelativeColumns(padding * 2);
            return padded;
        }

        /*
          width is invalid
        @Override
        public void drawHeader(final Table<String> table,
                               final String label,
                               final int index,
                               final TextGUIGraphics textGUIGraphics) {

            ThemeDefinition themeDefinition = table.getThemeDefinition();
            textGUIGraphics.applyThemeStyle(themeDefinition.getCustom("HEADER", themeDefinition.getNormal()));

            int columnWidth = getPreferredSize(table, "", index).getColumns();
            textGUIGraphics.putString(0, 0, align(label, columnWidth, HorizontalAlignment.CENTER));
        }
        */
    }
}
