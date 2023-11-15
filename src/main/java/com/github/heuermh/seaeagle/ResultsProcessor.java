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

import java.util.List;

import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Row;

/**
 * Results processor.
 */
abstract class ResultsProcessor implements AutoCloseable {

    /**
     * Notify this results processor of the specified list of columns.
     *
     * @param columns list of columns
     * @throws IOException if an error occurs
     */
    void columns(final List<ColumnInfo> columns) throws IOException {
        // empty
    }

    /**
     * Notify this results processor of the specified list of columns and list of rows.
     *
     * @param columns list of columns
     * @param rows list of rows
     * @throws IOException if an error occurs     *
     */
    void rows(final List<ColumnInfo> columns, final List<Row> rows) throws IOException {
        // empty
    }

    /**
     * Notify this results processor the results are complete.
     */
    void complete() throws IOException {
        // empty
    }

    @Override
    public void close() {
        // empty
    }
}
