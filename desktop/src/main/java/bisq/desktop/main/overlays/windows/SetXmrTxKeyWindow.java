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

import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;

import bisq.core.locale.Res;

import bisq.common.UserThread;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static javafx.beans.binding.Bindings.createBooleanBinding;

public class SetXmrTxKeyWindow extends Overlay<SetXmrTxKeyWindow> {

    private InputTextField txHashInputTextField, txKeyInputTextField;

    public SetXmrTxKeyWindow() {
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("setXMRTxKeyWindow.headline");

        width = 868;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();

        actionButton.disableProperty().bind(createBooleanBinding(() ->
                        txHashInputTextField.getText().isEmpty() || txKeyInputTextField.getText().isEmpty(),
                txHashInputTextField.textProperty(), txKeyInputTextField.textProperty()));

        applyStyles();
        display();
    }

    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        gridPane.getColumnConstraints().addAll(columnConstraints1);
    }

    @Nullable
    public String getTxHash() {
        return txHashInputTextField != null ? txHashInputTextField.getText() : null;
    }

    @Nullable
    public String getTxKey() {
        return txKeyInputTextField != null ? txKeyInputTextField.getText() : null;
    }

    private void addContent() {
        txHashInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("setXMRTxKeyWindow.txHash"), 10);
        txKeyInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("setXMRTxKeyWindow.txKey"));

        UserThread.runAfter(() -> {
            //todo: remove dev test data
            txHashInputTextField.setText("5e665addf6d7c6300670e8a89564ed12b5c1a21c336408e2835668f9a6a0d802");
            txKeyInputTextField.setText("f3ce66c9d395e5e460c8802b2c3c1fff04e508434f9738ee35558aac4678c906");
        }, 200, TimeUnit.MILLISECONDS);
    }
}
