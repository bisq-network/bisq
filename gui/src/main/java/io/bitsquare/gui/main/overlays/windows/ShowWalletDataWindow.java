/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.overlays.windows;

import io.bitsquare.btc.wallet.WalletsManager;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.locale.Res;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.addLabelCheckBox;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextArea;

public class ShowWalletDataWindow extends Overlay<ShowWalletDataWindow> {
    private static final Logger log = LoggerFactory.getLogger(ShowWalletDataWindow.class);

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

        width = 1200;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
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
        Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, ++rowIndex,
                Res.getWithCol("showWalletDataWindow.walletData"), "");
        TextArea textArea = labelTextAreaTuple2.second;
        Label label = labelTextAreaTuple2.first;
        label.setMinWidth(150);
        textArea.setPrefHeight(500);
        textArea.setStyle("-fx-font-size: 10;");
        CheckBox isUpdateCheckBox = addLabelCheckBox(gridPane, ++rowIndex,
                Res.get("showWalletDataWindow.includePrivKeys"), "").second;
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
