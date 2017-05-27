/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util;

import io.bitsquare.gui.main.MainView;

public class Layout {
    public static final double FIRST_ROW_DISTANCE = MainView.scale(20);
    public static final double GROUP_DISTANCE = MainView.scale(40);
    public static final double FIRST_ROW_AND_GROUP_DISTANCE = GROUP_DISTANCE + FIRST_ROW_DISTANCE;
    public static final double PADDING_WINDOW = MainView.scale(20);
    public static double PADDING = MainView.scale(10);
    public static double SPACING_H_BOX = MainView.scale(10);
    public static final double SPACING_V_BOX = MainView.scale(5);
    public static final double GRID_GAP = MainView.scale(5);
    public static final double LIST_ROW_HEIGHT = MainView.scale(34);
}
