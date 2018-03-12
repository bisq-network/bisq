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
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.PasswordTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.seedwords.SeedWordsView;
import bisq.desktop.main.account.settings.AccountSettingsView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.InputValidator;
import bisq.desktop.util.validation.PasswordValidator;

import bisq.core.btc.wallet.WalletsManager;
import bisq.core.crypto.ScryptUtil;

import bisq.common.locale.Res;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import static com.google.common.base.Preconditions.checkArgument;

@FxmlView
public class PasswordView extends ActivatableView<GridPane, Void> {

    private final WalletsManager walletsManager;
    private final PasswordValidator passwordValidator;
    private final Navigation navigation;

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
    private PasswordView(WalletsManager walletsManager, PasswordValidator passwordValidator, Navigation navigation) {
        this.walletsManager = walletsManager;
        this.passwordValidator = passwordValidator;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        headline = FormBuilder.addTitledGroupBg(root, gridRow, 2, "");
        passwordField = FormBuilder.addLabelPasswordTextField(root, gridRow, Res.get("password.enterPassword"), Layout.FIRST_ROW_DISTANCE).second;
        passwordField.setValidator(passwordValidator);
        passwordFieldChangeListener = (observable, oldValue, newValue) -> validatePasswords();

        Tuple2<Label, PasswordTextField> tuple2 = FormBuilder.addLabelPasswordTextField(root, ++gridRow, Res.get("password.confirmPassword"));
        repeatedPasswordLabel = tuple2.first;
        repeatedPasswordField = tuple2.second;
        repeatedPasswordField.setValidator(passwordValidator);
        repeatedPasswordFieldChangeListener = (observable, oldValue, newValue) -> validatePasswords();

        Tuple3<Button, BusyAnimation, Label> tuple = FormBuilder.addButtonBusyAnimationLabel(root, ++gridRow, "", 15);
        pwButton = tuple.first;
        BusyAnimation busyAnimation = tuple.second;
        Label deriveStatusLabel = tuple.third;
        pwButton.setDisable(true);

        setText();

        pwButton.setOnAction(e -> {
            if (!walletsManager.areWalletsEncrypted()) {
                new Popup<>().backgroundInfo(Res.get("password.backupReminder"))
                        .closeButtonText(Res.get("password.backupWasDone"))
                        .onClose(() -> onApplyPassword(busyAnimation, deriveStatusLabel))
                        .actionButtonTextWithGoTo("navigation.account.walletSeed")
                        .onAction(() -> {
                            navigation.setReturnPath(navigation.getCurrentPath());
                            //noinspection unchecked
                            navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, SeedWordsView.class);
                        })
                        .show();
            } else {
                onApplyPassword(busyAnimation, deriveStatusLabel);
            }
        });

        FormBuilder.addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.information"), Layout.GROUP_DISTANCE);
        FormBuilder.addMultilineLabel(root, gridRow, Res.get("account.password.info"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
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
                    new Popup<>()
                            .feedback(Res.get("password.walletDecrypted"))
                            .show();
                    passwordField.clear();
                    repeatedPasswordField.clear();
                    walletsManager.backupWallets();
                } else {
                    pwButton.setDisable(false);
                    new Popup<>()
                            .warning(Res.get("password.wrongPw"))
                            .show();
                }
            } else {
                try {
                    walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
                    new Popup<>()
                            .feedback(Res.get("password.walletEncrypted"))
                            .show();
                    passwordField.clear();
                    repeatedPasswordField.clear();
                    walletsManager.clearBackup();
                    walletsManager.backupWallets();
                } catch (Throwable t) {
                    new Popup<>()
                            .warning(Res.get("password.walletEncryptionFailed"))
                            .show();
                }
            }
            setText();
        });
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

