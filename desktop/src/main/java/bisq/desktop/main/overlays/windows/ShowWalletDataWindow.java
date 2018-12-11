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

import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Res;

import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;

import static bisq.desktop.util.FormBuilder.addLabelCheckBox;
import static bisq.desktop.util.FormBuilder.addTopLabelTextArea;

public class ShowWalletDataWindow extends Overlay<ShowWalletDataWindow> {
    private final WalletsManager walletsManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ShowWalletDataWindow(WalletsManager walletsManager) {
        this.walletsManager = walletsManager;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("showWalletDataWindow.walletData");

        width = 660;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setupKeyHandler(Scene scene) {
        if (!hideCloseButton) {
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    e.consume();
                    doClose();
                }
            });
        }
    }

    private void addContent() {
        gridPane.getColumnConstraints().get(0).setHalignment(HPos.LEFT);
        gridPane.getColumnConstraints().get(0).setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().get(1).setHgrow(Priority.SOMETIMES);

        Tuple2<Label, TextArea> labelTextAreaTuple2 = addTopLabelTextArea(gridPane, ++rowIndex,
                Res.get("showWalletDataWindow.walletData"), "");
        TextArea textArea = labelTextAreaTuple2.second;
        Label label = labelTextAreaTuple2.first;
        label.setMinWidth(150);
        textArea.setPrefHeight(500);
        textArea.getStyleClass().add("small-text");
        CheckBox isUpdateCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("showWalletDataWindow.includePrivKeys"));
        isUpdateCheckBox.setSelected(false);

        isUpdateCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            showWallet(textArea, isUpdateCheckBox);
        });

        showWallet(textArea, isUpdateCheckBox);

        actionButtonText(Res.get("shared.copyToClipboard"));
        onAction(() -> Utilities.copyToClipboard(textArea.getText()));
    }

    private void showWallet(TextArea textArea, CheckBox includePrivKeysCheckBox) {
        textArea.setText(walletsManager.getWalletsAsString(includePrivKeysCheckBox.isSelected()));
    }
}
