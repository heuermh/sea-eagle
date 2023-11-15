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

import java.util.Iterator;
import java.util.List;

import software.amazon.awssdk.services.athena.model.ColumnInfo;

/**
 * Tab delimited format.
 */
class TabDelimitedWithHeaderFormat extends TabDelimitedFormat {
    private boolean wroteHeader = false;

    TabDelimitedWithHeaderFormat(final Path resultsPath) {
        super(resultsPath);
    }

    @Override
    void columns(final List<ColumnInfo> columns) throws IOException {
        if (!wroteHeader) {
            StringBuilder sb = new StringBuilder();
            for (Iterator<ColumnInfo> it = columns.iterator(); it.hasNext(); ) {

                // fqn? catalogName(), schemaName(), tableName(), name()                
                sb.append(it.next().name());
                if (it.hasNext()) {
                    sb.append("\t");
                }
            }
            getWriter().println(sb);
            wroteHeader = true;
        }
    }
}
