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

package bisq.desktop.main.account.content.password;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.PasswordTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.backup.BackupView;
import bisq.desktop.main.account.content.seedwords.SeedWordsView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.PasswordValidator;

import bisq.core.btc.wallet.WalletsManager;
import bisq.core.crypto.ScryptUtil;
import bisq.core.locale.Res;

import bisq.common.util.Tuple4;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import com.jfoenix.validation.RequiredFieldValidator;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabel;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addPasswordTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static com.google.common.base.Preconditions.checkArgument;

@FxmlView
public class PasswordView extends ActivatableView<GridPane, Void> {

    private final WalletsManager walletsManager;
    private final PasswordValidator passwordValidator;
    private final Navigation navigation;

    private PasswordTextField passwordField;
    private PasswordTextField repeatedPasswordField;
    private AutoTooltipButton pwButton;
    private TitledGroupBg headline;
    private int gridRow = 0;
    private ChangeListener<Boolean> passwordFieldFocusChangeListener;
    private ChangeListener<String> passwordFieldTextChangeListener;
    private ChangeListener<String> repeatedPasswordFieldChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private PasswordView(WalletsManager walletsManager, PasswordValidator passwordValidator, Navigation navigation) {
        this.walletsManager = walletsManager;
        this.passwordValidator = passwordValidator;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        headline = addTitledGroupBg(root, gridRow, 3, "");
        passwordField = addPasswordTextField(root, gridRow, Res.get("password.enterPassword"), Layout.TWICE_FIRST_ROW_DISTANCE);
        final RequiredFieldValidator requiredFieldValidator = new RequiredFieldValidator();
        passwordField.getValidators().addAll(requiredFieldValidator, passwordValidator);
        passwordFieldFocusChangeListener = (observable, oldValue, newValue) -> {
            if (!newValue) validatePasswords();
        };

        passwordFieldTextChangeListener = (observable, oldvalue, newValue) -> {
            if (oldvalue != newValue) validatePasswords();
        };

        repeatedPasswordField = addPasswordTextField(root, ++gridRow, Res.get("password.confirmPassword"));
        requiredFieldValidator.setMessage(Res.get("validation.empty"));
        repeatedPasswordField.getValidators().addAll(requiredFieldValidator, passwordValidator);
        repeatedPasswordFieldChangeListener = (observable, oldValue, newValue) -> {
            if (oldValue != newValue) validatePasswords();
        };

        Tuple4<Button, BusyAnimation, Label, HBox> tuple = addButtonBusyAnimationLabel(root, ++gridRow, 0, "", 10);
        pwButton = (AutoTooltipButton) tuple.first;
        BusyAnimation busyAnimation = tuple.second;
        Label deriveStatusLabel = tuple.third;
        pwButton.setDisable(true);

        setText();

        pwButton.setOnAction(e -> {
            if (!walletsManager.areWalletsEncrypted()) {
                new Popup().backgroundInfo(Res.get("password.backupReminder"))
                        .actionButtonText(Res.get("password.setPassword"))
                        .onAction(() -> onApplyPassword(busyAnimation, deriveStatusLabel))
                        .secondaryActionButtonText(Res.get("password.makeBackup"))
                        .onSecondaryAction(() -> {
                            navigation.setReturnPath(navigation.getCurrentPath());
                            navigation.navigateTo(MainView.class, AccountView.class, BackupView.class);
                        })
                        .width(800)
                        .show();
            } else {
                onApplyPassword(busyAnimation, deriveStatusLabel);
            }
        });

        addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.information"), Layout.GROUP_DISTANCE);
        addMultilineLabel(root, gridRow, Res.get("account.password.info"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
    }

    private void onApplyPassword(BusyAnimation busyAnimation, Label deriveStatusLabel) {
        String password = passwordField.getText();
        checkArgument(password.length() < 500, Res.get("password.tooLong"));

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
                    backupWalletAndResetFields();
                } else {
                    pwButton.setDisable(false);
                    new Popup()
                            .warning(Res.get("password.wrongPw"))
                            .show();
                }
            } else {
                try {
                    walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
                    new Popup()
                            .feedback(Res.get("password.walletEncrypted"))
                            .show();
                    backupWalletAndResetFields();
                    walletsManager.clearBackup();
                } catch (Throwable t) {
                    new Popup()
                            .warning(Res.get("password.walletEncryptionFailed"))
                            .show();
                }
            }
            setText();
            updatePasswordListeners();
        });
    }

    private void backupWalletAndResetFields() {
        passwordField.clear();
        repeatedPasswordField.clear();
        walletsManager.backupWallets();
    }

    private void setText() {
        if (walletsManager.areWalletsEncrypted()) {
            pwButton.updateText(Res.get("account.password.removePw.button"));
            headline.setText(Res.get("account.password.removePw.headline"));

            repeatedPasswordField.setVisible(false);
            repeatedPasswordField.setManaged(false);
        } else {
            pwButton.updateText(Res.get("account.password.setPw.button"));
            headline.setText(Res.get("account.password.setPw.headline"));

            repeatedPasswordField.setVisible(true);
            repeatedPasswordField.setManaged(true);
        }
    }

    @Override
    protected void activate() {
        updatePasswordListeners();

        repeatedPasswordField.textProperty().addListener(repeatedPasswordFieldChangeListener);
    }

    private void updatePasswordListeners() {
        passwordField.focusedProperty().removeListener(passwordFieldFocusChangeListener);
        passwordField.textProperty().removeListener(passwordFieldTextChangeListener);

        if (walletsManager.areWalletsEncrypted()) {
            passwordField.textProperty().addListener(passwordFieldTextChangeListener);
        } else {
            passwordField.focusedProperty().addListener(passwordFieldFocusChangeListener);
        }
    }

    @Override
    protected void deactivate() {
        passwordField.focusedProperty().removeListener(passwordFieldFocusChangeListener);
        passwordField.textProperty().removeListener(passwordFieldTextChangeListener);
        repeatedPasswordField.textProperty().removeListener(repeatedPasswordFieldChangeListener);

    }

    private void validatePasswords() {
        passwordValidator.setPasswordsMatch(true);

        if (passwordField.validate()) {
            if (walletsManager.areWalletsEncrypted()) {
                pwButton.setDisable(false);
                return;
            } else {
                if (repeatedPasswordField.validate()) {
                    if (passwordField.getText().equals(repeatedPasswordField.getText())) {
                        pwButton.setDisable(false);
                        return;
                    } else {
                        passwordValidator.setPasswordsMatch(false);
                        repeatedPasswordField.validate();
                    }
                }
            }
        }
        pwButton.setDisable(true);
    }
}

