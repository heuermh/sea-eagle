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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sparse formatted table.
 */
class SparseTable extends PrettyTable {

    SparseTable(final boolean skipHeaderWhenEmpty,
                final char horizontalChar,
                final int leftPad) {

        super(skipHeaderWhenEmpty, ' ', horizontalChar, ' ', leftPad);
    }


    @Override
    protected List<String> formatHeader() {
        List<String> header = new ArrayList<>();
        header.addAll(headerLines());
        header.addAll(formatHrule());
        return header;
    }

    @Override
    protected List<String> formatFooter() {
        return Collections.<String>emptyList();
    }
}
