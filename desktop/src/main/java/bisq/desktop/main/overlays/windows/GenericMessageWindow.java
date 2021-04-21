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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.Layout;

import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTextArea;
import static com.google.common.base.Preconditions.checkNotNull;

public class GenericMessageWindow extends Overlay<GenericMessageWindow> {
    private String preamble;

    public GenericMessageWindow() {
        super();
    }

    public void show() {
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    public GenericMessageWindow preamble(String preamble) {
        this.preamble = preamble;
        return this;
    }

    private void addContent() {
        if (preamble != null) {
            Label label = addMultilineLabel(gridPane, ++rowIndex, preamble, 10);
            label.setPrefSize(Layout.INITIAL_WINDOW_WIDTH, Layout.INITIAL_WINDOW_HEIGHT * 0.1);
        }
        checkNotNull(message, "message must not be null");
        TextArea textArea = addTextArea(gridPane, ++rowIndex, "", 10);
        textArea.setText(message);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        // sizes the textArea to fit within its parent container
        double verticalSizePercentage = ensureRange(countLines(message) / 20.0, 0.2, 0.7);
        textArea.setPrefSize(Layout.INITIAL_WINDOW_WIDTH, Layout.INITIAL_WINDOW_HEIGHT * verticalSizePercentage);
    }

    private static int countLines(String str) {
        String[] lines = str.split("\r\n|\r|\n");
        return  lines.length;
    }

    private static double ensureRange(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }
}
