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

package bisq.desktop.main.overlays.windows.supporttool;

import bisq.desktop.components.BisqTextArea;

import javafx.scene.control.TextArea;

public class ExportPane extends CommonPane {

    private final InputsPane inputsPane;
    private final TextArea exportHex;

    ExportPane(InputsPane inputsPane) {
        this.inputsPane = inputsPane;
        exportHex = new BisqTextArea();
        exportHex.setEditable(false);
        exportHex.setWrapText(true);
        exportHex.setPrefSize(800, 250);
        add(exportHex, 0, 1);
    }

    @Override
    public void activate() {
        // export pane is populated from the inputs pane fields
        exportHex.setText(inputsPane.generateExportText());
        super.activate();
    }

    @Override
    public String getName() {
        return "Export";
    }
}
