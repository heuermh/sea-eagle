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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.Row;

/**
 * Sparse table format.
 */
class SparseTableFormat extends TabDelimitedFormat {
    private boolean readHeader = false;
    private final SparseTable table;

    SparseTableFormat(final Path resultsPath, final int leftPad) {
        super(resultsPath);
        table = new SparseTable(true, '-', leftPad);
    }

    @Override
    void columns(final List<ColumnInfo> columns) throws IOException {
        if (!readHeader) {
            for (ColumnInfo columnInfo : columns) {
                String columnName = columnInfo.name();
                String columnAlign = "varchar".equals(columnInfo.type()) ? "l" : "r";
                table.addColumn(columnName, columnAlign);
            }
            readHeader = true;
        }
    }

    @Override
    void rows(final List<ColumnInfo> columns, final List<Row> rows) throws IOException {
        for (Row row : rows) {
            if (seenHeaderRow || !isHeaderRow(columns, row)) {
                List<String> rowValues = new ArrayList<>(row.data().size());
                for (Datum datum : row.data()) {
                    rowValues.add(datum.varCharValue());
                }
                table.addRow(rowValues);
            }
        }
    }

    @Override
    void complete() throws IOException {
        getWriter().println(table.toString());
    }
}
