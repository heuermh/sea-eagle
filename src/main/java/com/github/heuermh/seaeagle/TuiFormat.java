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

import java.nio.file.Path;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.googlecode.lanterna.gui2.AsynchronousTextGUIThread;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

import com.googlecode.lanterna.gui2.table.Table;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;

import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.Row;

/**
 * Text-based UI (tui) format.
 */
class TuiFormat extends ResultsProcessor {
    private boolean seenHeader = false;
    private boolean seenHeaderRow = false;
    private Table<String> table;

    TuiFormat() {
        // empty
    }

    @Override
    void columns(final List<ColumnInfo> columns) throws IOException {
        if (!seenHeader) {
            List<String> columnNames = new ArrayList<>(columns.size());
            for (ColumnInfo columnInfo : columns) {
                columnNames.add(columnInfo.name());
            }
            table = new Table<String>(columnNames.toArray(new String[0]));
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
        //window.waitUntilClosed();

        // todo: default styling is not so good
        window.setHints(Arrays.asList(Window.Hint.NO_DECORATIONS, Window.Hint.FULL_SCREEN, Window.Hint.FIT_TERMINAL_WINDOW));

        Panel panel = new Panel();
        // todo: left panel for --left-pad
        panel.setLayoutManager(new BorderLayout());
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
}
