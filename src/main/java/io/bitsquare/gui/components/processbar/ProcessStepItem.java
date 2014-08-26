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

package io.bitsquare.gui.components.processbar;

import io.bitsquare.gui.util.Colors;
import javafx.scene.paint.Paint;

public class ProcessStepItem {
    private final String label;
    private final Paint color;
    private final boolean progressIndicator;

    public ProcessStepItem(String label) {
        this(label, Colors.BLUE, false);
    }

    public ProcessStepItem(String label, Paint color) {
        this(label, color, false);
    }

    private ProcessStepItem(String label, Paint color, @SuppressWarnings("SameParameterValue") boolean hasProgressIndicator) {
        this.label = label;
        this.color = color;
        this.progressIndicator = hasProgressIndicator;
    }

    public String getLabel() {
        return label;
    }

    public Paint getColor() {
        return color;
    }

    public boolean hasProgressIndicator() {
        return progressIndicator;
    }
}
