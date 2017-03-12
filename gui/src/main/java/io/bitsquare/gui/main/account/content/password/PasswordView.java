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

package io.bitsquare.gui.main.account.content.password;

import io.bitsquare.btc.wallet.WalletsManager;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.crypto.ScryptUtil;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.BusyAnimation;
import io.bitsquare.gui.components.PasswordTextField;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.PasswordValidator;
import io.bitsquare.locale.Res;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import static com.google.inject.internal.util.$Preconditions.checkArgument;
import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class PasswordView extends ActivatableView<GridPane, Void> {

    private final WalletsManager walletsManager;
    private final PasswordValidator passwordValidator;

    private PasswordTextField passwordField;
    private PasswordTextField repeatedPasswordField;
    private Button pwButton;
    private TitledGroupBg headline;
    private int gridRow = 0;
    private Label repeatedPasswordLabel;
    private ChangeListener<String> passwordFieldChangeListener;
    private ChangeListener<String> repeatedPasswordFieldChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private PasswordView(WalletsManager walletsManager, PasswordValidator passwordValidator) {
        this.walletsManager = walletsManager;
        this.passwordValidator = passwordValidator;
    }

    @Override
    public void initialize() {
        headline = addTitledGroupBg(root, gridRow, 2, "");
        passwordField = addLabelPasswordTextField(root, gridRow, Res.get("password.enterPassword"), Layout.FIRST_ROW_DISTANCE).second;
        passwordField.setValidator(passwordValidator);
        passwordFieldChangeListener = (observable, oldValue, newValue) -> validatePasswords();

        Tuple2<Label, PasswordTextField> tuple2 = addLabelPasswordTextField(root, ++gridRow, Res.get("password.confirmPassword"));
        repeatedPasswordLabel = tuple2.first;
        repeatedPasswordField = tuple2.second;
        repeatedPasswordField.setValidator(passwordValidator);
        repeatedPasswordFieldChangeListener = (observable, oldValue, newValue) -> validatePasswords();

        Tuple3<Button, BusyAnimation, Label> tuple = addButtonBusyAnimationLabel(root, ++gridRow, "", 15);
        pwButton = tuple.first;
        BusyAnimation busyAnimation = tuple.second;
        Label deriveStatusLabel = tuple.third;
        pwButton.setDisable(true);

        setText();

        pwButton.setOnAction(e -> {
            String password = passwordField.getText();
            checkArgument(password.length() < 50, Res.get("password.tooLong"));

            pwButton.setDisable(true);
            deriveStatusLabel.setText(Res.get("password.deriveKey"));
            busyAnimation.play();

            KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
            ScryptUtil.deriveKeyWithScrypt(keyCrypterScrypt, password, aesKey -> {
                deriveStatusLabel.setText("");
                busyAnimation.stop();

                if (walletsManager.areWalletsEncrypted()) {
                    if (walletsManager.checkAESKey(aesKey)) {
                        walletsManager.decryptWallets(aesKey);
                        new Popup()
                                .feedback(Res.get("password.walletDecrypted"))
                                .show();
                        passwordField.setText("");
                        repeatedPasswordField.setText("");
                        walletsManager.backupWallets();
                    } else {
                        pwButton.setDisable(false);
                        new Popup()
                                .warning(Res.get("password.wrongPw"))
                                .show();
                    }
                } else {
                    walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
                    new Popup()
                            .feedback(Res.get("password.walletEncrypted"))
                            .show();
                    passwordField.setText("");
                    repeatedPasswordField.setText("");
                    walletsManager.clearBackup();
                    walletsManager.backupWallets();
                }
                setText();
            });
        });

        addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.information"), Layout.GROUP_DISTANCE);
        addMultilineLabel(root, gridRow, Res.get("account.password.info"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);

    }

    private void setText() {
        if (walletsManager.areWalletsEncrypted()) {
            pwButton.setText(Res.get("account.password.removePw.button"));
            headline.setText(Res.get("account.password.removePw.headline"));
            repeatedPasswordField.setVisible(false);
            repeatedPasswordField.setManaged(false);
            repeatedPasswordLabel.setVisible(false);
            repeatedPasswordLabel.setManaged(false);
        } else {
            pwButton.setText(Res.get("account.password.setPw.button"));
            headline.setText(Res.get("account.password.setPw.headline"));
            repeatedPasswordField.setVisible(true);
            repeatedPasswordField.setManaged(true);
            repeatedPasswordLabel.setVisible(true);
            repeatedPasswordLabel.setManaged(true);
        }
    }

    @Override
    protected void activate() {
        passwordField.textProperty().addListener(passwordFieldChangeListener);
        repeatedPasswordField.textProperty().addListener(repeatedPasswordFieldChangeListener);

    }

    @Override
    protected void deactivate() {
        passwordField.textProperty().removeListener(passwordFieldChangeListener);
        repeatedPasswordField.textProperty().removeListener(repeatedPasswordFieldChangeListener);

    }

    private void validatePasswords() {
        passwordValidator.setExternalValidationResult(null);
        InputValidator.ValidationResult result = passwordValidator.validate(passwordField.getText());
        if (result.isValid) {
            if (walletsManager.areWalletsEncrypted()) {
                pwButton.setDisable(false);
                return;
            } else {
                result = passwordValidator.validate(repeatedPasswordField.getText());

                if (result.isValid) {
                    if (passwordField.getText().equals(repeatedPasswordField.getText())) {
                        pwButton.setDisable(false);
                        return;
                    } else {
                        passwordValidator.setExternalValidationResult(new InputValidator.ValidationResult(false,
                                Res.get("password.passwordsDoNotMatch")));
                    }
                }
            }
        }
        pwButton.setDisable(true);
    }
}

