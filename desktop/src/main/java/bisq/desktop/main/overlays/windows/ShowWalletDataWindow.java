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
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Res;

import bisq.common.UserThread;
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
    private final BtcWalletService btcWalletService;
    private final WalletPasswordWindow walletPasswordWindow;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ShowWalletDataWindow(WalletsManager walletsManager,
                                BtcWalletService btcWalletService,
                                WalletPasswordWindow walletPasswordWindow) {
        this.walletsManager = walletsManager;
        this.btcWalletService = btcWalletService;
        this.walletPasswordWindow = walletPasswordWindow;
        type = Type.Attention;
    }

    public void show() {
        UserThread.execute(() -> {
            new Popup().warning(Res.get("account.keys.clipboard.warning"))
                    .actionButtonText(Res.get("shared.continueAnyway"))
                    .onClose(this::doClose)
                    .onAction(this::showStep2)
                    .show();
        });
    }

    private void showStep2() {
        if (btcWalletService.isEncrypted()) {
            UserThread.execute(() -> walletPasswordWindow.onAesKey(aesKey -> showStep3()).show());
        } else {
            showStep3();
        }
    }

    private void showStep3() {
        if (headLine == null)
            headLine = Res.get("showWalletDataWindow.walletData");

        width = 1000;
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
