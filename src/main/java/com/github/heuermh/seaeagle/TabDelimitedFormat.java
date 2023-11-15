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

import static org.dishevelled.compress.Writers.writer;

import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;

import java.util.Iterator;
import java.util.List;

import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.Row;

/**
 * Tab delimited format.
 */
class TabDelimitedFormat extends ResultsProcessor {
    private final Path resultsPath;
    private PrintWriter writer;
    protected boolean seenHeaderRow = false;

    TabDelimitedFormat(final Path resultsPath) {
        this.resultsPath = resultsPath;
    }

    protected final PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = writer(resultsPath);
        }
        return writer;
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
                StringBuilder sb = new StringBuilder();
                for (Iterator<Datum> it = row.data().iterator(); it.hasNext(); ) {
                    sb.append(it.next().varCharValue());
                    if (it.hasNext()) {
                        sb.append("\t");
                    }
                }
                getWriter().println(sb);
            }
        }
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.close();
        }
    }
}
