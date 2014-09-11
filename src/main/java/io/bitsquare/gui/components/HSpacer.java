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

package io.bitsquare.gui.components;


import javafx.scene.layout.*;

// TODO remove and use margin or padding instead
@Deprecated
public class HSpacer extends Pane {
    public HSpacer() {
    }

    public HSpacer(double width) {
        setPrefWidth(width);
    }

    @Override
    protected double computePrefWidth(double width) {
        return getPrefWidth();
    }
}

