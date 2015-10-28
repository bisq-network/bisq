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

package io.bitsquare.gui.popups;

import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.ScryptUtil;
import io.bitsquare.gui.components.PasswordTextField;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.gui.util.validation.PasswordValidator;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.reactfx.util.FxTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;

public class WalletPasswordPopup extends Popup {
    private static final Logger log = LoggerFactory.getLogger(WalletPasswordPopup.class);
    private final WalletService walletService;
    private Button unlockButton;
    private AesKeyHandler aesKeyHandler;
    private PasswordTextField passwordTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface AesKeyHandler {
        void onAesKey(KeyParameter aesKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletPasswordPopup(WalletService walletService) {
        this.walletService = walletService;
    }

    public WalletPasswordPopup show() {
        if (gridPane != null) {
            rowIndex = -1;
            gridPane.getChildren().clear();
        }

        if (headLine == null)
            headLine = "Enter password to unlock";

        createGridPane();
        addHeadLine();
        addInputFields();
        addButtons();
        createPopup();

        return this;
    }

    public WalletPasswordPopup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }

    public WalletPasswordPopup onAesKey(AesKeyHandler aesKeyHandler) {
        this.aesKeyHandler = aesKeyHandler;
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addInputFields() {
        Label label = new Label("Enter password:");
        label.setWrapText(true);
        GridPane.setMargin(label, new Insets(3, 0, 0, 0));
        GridPane.setRowIndex(label, ++rowIndex);

        passwordTextField = new PasswordTextField();
        GridPane.setMargin(passwordTextField, new Insets(3, 0, 0, 0));
        GridPane.setRowIndex(passwordTextField, rowIndex);
        GridPane.setColumnIndex(passwordTextField, 1);
        PasswordValidator passwordValidator = new PasswordValidator();
        passwordTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            unlockButton.setDisable(!passwordValidator.validate(newValue).isValid);
        });
        gridPane.getChildren().addAll(label, passwordTextField);
    }

    private void addButtons() {
        unlockButton = new Button("Unlock");
        unlockButton.setDefaultButton(true);
        unlockButton.setDisable(true);
        unlockButton.setOnAction(e -> checkPassword());

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(unlockButton, cancelButton);
        gridPane.getChildren().add(hBox);
    }

    private void checkPassword() {
        String password = passwordTextField.getText();
        Wallet wallet = walletService.getWallet();
        KeyCrypterScrypt keyCrypterScrypt = (KeyCrypterScrypt) wallet.getKeyCrypter();
        if (keyCrypterScrypt != null) {
            ScryptUtil.deriveKeyWithScrypt(keyCrypterScrypt, password, aesKey -> {
                if (wallet.checkAESKey(aesKey)) {
                    if (aesKeyHandler != null)
                        aesKeyHandler.onAesKey(aesKey);

                    hide();
                } else {
                    FxTimer.runLater(Duration.ofMillis(Transitions.DEFAULT_DURATION), () -> new Popup()
                            .headLine("Wrong password")
                            .message("Please try entering your password again, carefully checking for typos or spelling errors.")
                            .onClose(() -> blurAgain()).show());
                }
            });
        } else {
            log.error("wallet.getKeyCrypter() is null, than must not happen.");
        }
    }
}
