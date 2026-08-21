/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util;

public class Layout {
    public static final double INITIAL_WINDOW_WIDTH = 1200;
    public static final double INITIAL_WINDOW_HEIGHT = 710; //740
    public static final double MIN_WINDOW_WIDTH = 1020;
    public static final double MIN_WINDOW_HEIGHT = 620;
    public static final double FIRST_ROW_DISTANCE = 20d;
    public static final double COMPACT_FIRST_ROW_DISTANCE = 10d;
    public static final double TWICE_FIRST_ROW_DISTANCE = 20d * 2;
    public static final double FLOATING_LABEL_DISTANCE = 18d;
    public static final double GROUP_DISTANCE = 40d;
    public static final double COMPACT_GROUP_DISTANCE = 30d;
    public static final double GROUP_DISTANCE_WITHOUT_SEPARATOR = 20d;
    public static final double FIRST_ROW_AND_GROUP_DISTANCE = GROUP_DISTANCE + FIRST_ROW_DISTANCE;
    public static final double COMPACT_FIRST_ROW_AND_GROUP_DISTANCE = COMPACT_GROUP_DISTANCE + FIRST_ROW_DISTANCE;
    public static final double COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE = COMPACT_GROUP_DISTANCE + COMPACT_FIRST_ROW_DISTANCE;
    public static final double COMPACT_FIRST_ROW_AND_GROUP_DISTANCE_WITHOUT_SEPARATOR = GROUP_DISTANCE_WITHOUT_SEPARATOR + COMPACT_FIRST_ROW_DISTANCE;
    public static final double TWICE_FIRST_ROW_AND_GROUP_DISTANCE = GROUP_DISTANCE + TWICE_FIRST_ROW_DISTANCE;
    public static final double PADDING_WINDOW = 20d;
    public static double PADDING = 10d;
    public static double SPACING_H_BOX = 10d;
    public static final double SPACING_V_BOX = 5d;
    public static final double GRID_GAP = 5d;
    public static final double LIST_ROW_HEIGHT = 34;
}
