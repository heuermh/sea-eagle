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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Formatting.
 */
final class Formatting {

    /**
     * Abbreviate the specified value to the specified width, if necessary.
     *
     * @param value value to abbreviate, if any
     * @param width maximum width with abbreviation
     * @return the specified value abbreviated to the specified width, if necessary
     */
    static String abbreviate(final String value, final int width) {
        String suffix = ".".repeat(Math.min(width, 3));
        return value.length() <= width ? value : value.substring(0, width - suffix.length()) + suffix;
    }

    /**
     * Calculate centered padding for the specified interval and size.
     *
     * @param interval interval
     * @param size size, must be less than or equal to <code>interval</code>
     * @return centered padding for the specified interval and size
     */
    static int[] centeredPadding(final int interval, final int size) {
        if (size > interval) {
            throw new IllegalArgumentException("size " + size + " must be less than or equal to interval " + interval);
        }
        boolean sameParity = (interval % 2) == (size % 2);
        int padding = (interval - size) / 2;

        if (sameParity) {
            return new int[]{ padding, padding };
        }
        else {
            return new int[]{ padding, padding + 1 };
        }
    }

    /**
     * Center align the specified value within the specified width.
     *
     * @param value value to center align
     * @param width width, must be less than or equal to <code>value.length</code>
     * @return the specified value center aligned within the specified width
     */
    static String alignCenter(final String value, final int width) {
        int[] paddings = centeredPadding(width, value.length());
        return " " + " ".repeat(paddings[0]) + value + " ".repeat(paddings[1]) + " ";
    }

    /**
     * Left align the specified value within the specified width.
     *
     * @param value value to left align
     * @param width width, must be less than or equal to <code>value.length</code>
     * @return the specified value left aligned within the specified width
     */
    static String alignLeft(final String value, final int width) {
        return String.format(" %-" + width + "s ", value);
    }

    /**
     * Right align the specified value within the specified width.
     *
     * @param value value to right align
     * @param width width, must be less than or equal to <code>value.length</code>
     * @return the specified value right aligned within the specified width
     */
    static String alignRight(final String value, final int width) {
        return String.format(" %" + width + "s ", value);
    }

    /**
     * Align the specified value within the specified width.
     *
     * @param value value to align
     * @param width width, must be less than or equal to <code>value.length</code>
     * @param horizontalAlignment horizontal alignment, must not be null
     * @return the specified value right aligned within the specified width
     */
    static String align(final String value, final int width, final HorizontalAlignment horizontalAlignment) {
        checkNotNull(horizontalAlignment);
        switch (horizontalAlignment) {
            case LEFT:
                return alignLeft(value, width);
            case CENTER:
                return alignCenter(value, width);
            case RIGHT:
            default:
                return alignRight(value, width);
        }
    }

    /**
     * Align the specified list of values within the specified height and width.
     *
     * @param values list of values to align, must not be null
     * @param height height
     * @param width width
     * @param horizontalAlignment horizontal alignment, must not be null
     * @param verticalAlignment vertical alignment, must not be null
     * @return the specified list of values aligned within the specified height and width
     */
    static List<String> align(final List<String> values,
                              final int height,
                              final int width,
                              final HorizontalAlignment horizontalAlignment,
                              final VerticalAlignment verticalAlignment) {

        List<String> topLines = new ArrayList<>();
        List<String> bottomLines = new ArrayList<>();

        switch (verticalAlignment) {
            case CENTER:
                int[] paddings = centeredPadding(height, values.size());
                topLines = Collections.nCopies(paddings[0], " ".repeat(width + 2));
                bottomLines = Collections.nCopies(paddings[1], " ".repeat(width + 2));
                break;
            case BOTTOM:
                topLines = Collections.nCopies(height - values.size(), " ".repeat(width + 2));
                break;
            case TOP:
            default:
                bottomLines = Collections.nCopies(height - values.size(), " ".repeat(width + 2));
        }

        List<String> contentLines = new ArrayList<>();
        for (String value : values) {
            contentLines.add(align(value, width, horizontalAlignment));
        }

        List<String> result = new ArrayList<>(topLines);
        result.addAll(contentLines);
        result.addAll(bottomLines);
        return result;
    }
}
