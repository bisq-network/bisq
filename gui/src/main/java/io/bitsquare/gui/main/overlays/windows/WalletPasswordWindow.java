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

package io.bitsquare.gui.main.overlays.windows;

import com.google.common.base.Splitter;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.wallet.WalletsManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.crypto.ScryptUtil;
import io.bitsquare.gui.components.BusyAnimation;
import io.bitsquare.gui.components.PasswordTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.gui.util.validation.PasswordValidator;
import io.bitsquare.locale.BSResources;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static com.google.inject.internal.util.$Preconditions.checkArgument;
import static io.bitsquare.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createBooleanBinding;

public class WalletPasswordWindow extends Overlay<WalletPasswordWindow> {
    private static final Logger log = LoggerFactory.getLogger(WalletPasswordWindow.class);
    private Button unlockButton;
    private AesKeyHandler aesKeyHandler;
    private PasswordTextField passwordTextField;
    private Button forgotPasswordButton;
    private Button restoreButton;
    private TextArea btcSeedWordsTextArea, squSeedWordsTextArea;
    private DatePicker restoreDatePicker;
    private final SimpleBooleanProperty btcSeedWordsValid = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty squSeedWordsValid = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty dateValid = new SimpleBooleanProperty(false);
    private final BooleanProperty btcSeedWordsEdited = new SimpleBooleanProperty();
    private final BooleanProperty squSeedWordsEdited = new SimpleBooleanProperty();
    private ChangeListener<String> changeListener;
    private ChangeListener<String> btcWordsTextAreaChangeListener, squWordsTextAreaChangeListener;
    private ChangeListener<Boolean> datePickerChangeListener;
    private ChangeListener<Boolean> seedWordsValidChangeListener;
    private ChangeListener<LocalDate> dateChangeListener;
    private LocalDate walletCreationDate;
    private final WalletsManager walletsManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface AesKeyHandler {
        void onAesKey(KeyParameter aesKey);
    }

    @Inject
    private WalletPasswordWindow(WalletsManager walletsManager) {
        this.walletsManager = walletsManager;
        type = Type.Attention;
        width = 800;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void show() {
        if (gridPane != null) {
            rowIndex = -1;
            gridPane.getChildren().clear();
        }

        if (headLine == null)
            headLine = "Enter password to unlock";

        createGridPane();
        addHeadLine();
        addSeparator();
        addInputFields();
        addButtons();
        applyStyles();
        display();
    }

    public WalletPasswordWindow onAesKey(AesKeyHandler aesKeyHandler) {
        this.aesKeyHandler = aesKeyHandler;
        return this;
    }

    @Override
    protected void cleanup() {
        if (passwordTextField != null)
            passwordTextField.textProperty().removeListener(changeListener);

        if (seedWordsValidChangeListener != null) {
            btcSeedWordsValid.removeListener(seedWordsValidChangeListener);
            dateValid.removeListener(datePickerChangeListener);
            btcSeedWordsTextArea.textProperty().removeListener(btcWordsTextAreaChangeListener);
            squSeedWordsTextArea.textProperty().removeListener(squWordsTextAreaChangeListener);
            restoreDatePicker.valueProperty().removeListener(dateChangeListener);
            restoreButton.disableProperty().unbind();
            restoreButton.setOnAction(null);
            btcSeedWordsTextArea.setText("");
            squSeedWordsTextArea.setText("");
            restoreDatePicker.setValue(null);
            btcSeedWordsTextArea.getStyleClass().remove("validation_error");
            squSeedWordsTextArea.getStyleClass().remove("validation_error");
            restoreDatePicker.getStyleClass().remove("validation_error");
        }
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
        changeListener = (observable, oldValue, newValue) -> unlockButton.setDisable(!passwordValidator.validate(newValue).isValid);
        passwordTextField.textProperty().addListener(changeListener);
        gridPane.getChildren().addAll(label, passwordTextField);
    }

    private void addButtons() {
        BusyAnimation busyAnimation = new BusyAnimation(false);
        Label deriveStatusLabel = new Label();

        unlockButton = new Button("Unlock");
        unlockButton.setDefaultButton(true);
        unlockButton.setDisable(true);
        unlockButton.setOnAction(e -> {
            String password = passwordTextField.getText();
            checkArgument(password.length() < 50, "Password must be less then 50 characters.");
            KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
            if (keyCrypterScrypt != null) {
                busyAnimation.play();
                deriveStatusLabel.setText("Derive key from password");
                ScryptUtil.deriveKeyWithScrypt(keyCrypterScrypt, password, aesKey -> {
                    if (walletsManager.checkAESKey(aesKey)) {
                        if (aesKeyHandler != null)
                            aesKeyHandler.onAesKey(aesKey);

                        hide();
                    } else {
                        busyAnimation.stop();
                        deriveStatusLabel.setText("");

                        UserThread.runAfter(() -> new Popup()
                                .warning("You entered the wrong password.\n\n" +
                                        "Please try entering your password again, carefully checking for typos or spelling errors.")
                                .onClose(this::blurAgain).show(), Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS);
                    }
                });
            } else {
                log.error("wallet.getKeyCrypter() is null, that must not happen.");
            }
        });

        forgotPasswordButton = new Button("Forgot password?");
        forgotPasswordButton.setOnAction(e -> {
            forgotPasswordButton.setDisable(true);
            unlockButton.setDefaultButton(false);
            showRestoreScreen();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        HBox hBox = new HBox();
        hBox.setMinWidth(560);
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.setAlignment(Pos.CENTER_LEFT);
        if (hideCloseButton)
            hBox.getChildren().addAll(unlockButton, forgotPasswordButton, busyAnimation, deriveStatusLabel);
        else
            hBox.getChildren().addAll(unlockButton, cancelButton);
        gridPane.getChildren().add(hBox);


        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
    }

    private void showRestoreScreen() {
        Label headLine2Label = new Label(BSResources.get("Restore wallet from seed words"));
        headLine2Label.setId("popup-headline");
        headLine2Label.setMouseTransparent(true);
        GridPane.setHalignment(headLine2Label, HPos.LEFT);
        GridPane.setRowIndex(headLine2Label, ++rowIndex);
        GridPane.setColumnSpan(headLine2Label, 2);
        GridPane.setMargin(headLine2Label, new Insets(30, 0, 0, 0));
        gridPane.getChildren().add(headLine2Label);

        Separator separator = new Separator();
        separator.setMouseTransparent(true);
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #ccc;");
        GridPane.setHalignment(separator, HPos.CENTER);
        GridPane.setRowIndex(separator, ++rowIndex);
        GridPane.setColumnSpan(separator, 2);

        gridPane.getChildren().add(separator);

        Tuple2<Label, TextArea> tuple = addLabelTextArea(gridPane, ++rowIndex, "BTC wallet seed words:", "", 5);
        btcSeedWordsTextArea = tuple.second;
        btcSeedWordsTextArea.setPrefHeight(60);
        btcSeedWordsTextArea.setStyle("-fx-border-color: #ddd;");

        Tuple2<Label, TextArea> tuple2 = addLabelTextArea(gridPane, ++rowIndex, "SQU wallet seed words:", "", 5);
        squSeedWordsTextArea = tuple2.second;
        squSeedWordsTextArea.setPrefHeight(60);
        squSeedWordsTextArea.setStyle("-fx-border-color: #ddd;");

        Tuple2<Label, DatePicker> labelDatePickerTuple2 = addLabelDatePicker(gridPane, ++rowIndex, "Creation Date:");
        restoreDatePicker = labelDatePickerTuple2.second;
        restoreButton = addButton(gridPane, ++rowIndex, "Restore wallets");
        restoreButton.setDefaultButton(true);
        stage.setHeight(340);


        // wallet creation date is not encrypted
        walletCreationDate = Instant.ofEpochSecond(walletsManager.getChainSeedCreationTimeSeconds()).atZone(ZoneId.systemDefault()).toLocalDate();

        restoreButton.disableProperty().bind(createBooleanBinding(() -> !btcSeedWordsValid.get() || !dateValid.get() || !btcSeedWordsEdited.get(),
                btcSeedWordsValid, dateValid, btcSeedWordsEdited));


        seedWordsValidChangeListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                btcSeedWordsTextArea.getStyleClass().remove("validation_error");
            } else {
                btcSeedWordsTextArea.getStyleClass().add("validation_error");
            }
        };

        btcWordsTextAreaChangeListener = (observable, oldValue, newValue) -> {
            btcSeedWordsEdited.set(true);
            try {
                MnemonicCode codec = new MnemonicCode();
                codec.check(Splitter.on(" ").splitToList(newValue));
                btcSeedWordsValid.set(true);
            } catch (IOException | MnemonicException e) {
                btcSeedWordsValid.set(false);
            }
        };
        squWordsTextAreaChangeListener = (observable, oldValue, newValue) -> {
            squSeedWordsEdited.set(true);
            try {
                MnemonicCode codec = new MnemonicCode();
                codec.check(Splitter.on(" ").splitToList(newValue));
                squSeedWordsValid.set(true);
            } catch (IOException | MnemonicException e) {
                squSeedWordsValid.set(false);
            }
        };


        datePickerChangeListener = (observable, oldValue, newValue) -> {
            if (newValue)
                restoreDatePicker.getStyleClass().remove("validation_error");
            else
                restoreDatePicker.getStyleClass().add("validation_error");
        };
        dateChangeListener = (observable, oldValue, newValue) -> dateValid.set(walletCreationDate.equals(newValue));

        btcSeedWordsValid.addListener(seedWordsValidChangeListener);
        dateValid.addListener(datePickerChangeListener);
        btcSeedWordsTextArea.textProperty().addListener(btcWordsTextAreaChangeListener);
        squSeedWordsTextArea.textProperty().addListener(squWordsTextAreaChangeListener);
        restoreDatePicker.valueProperty().addListener(dateChangeListener);
        restoreButton.disableProperty().bind(createBooleanBinding(() -> !btcSeedWordsValid.get() || !dateValid.get() || !btcSeedWordsEdited.get(),
                btcSeedWordsValid, dateValid, btcSeedWordsEdited));

        restoreButton.setOnAction(e -> onRestore());

        btcSeedWordsTextArea.getStyleClass().remove("validation_error");
        squSeedWordsTextArea.getStyleClass().remove("validation_error");
        restoreDatePicker.getStyleClass().remove("validation_error");

        layout();
    }

    private void onRestore() {
        if (walletsManager.hasPositiveBalance()) {
            new Popup()
                    .warning("Your bitcoin wallet is not empty.\n\n" +
                            "You must empty this wallet before attempting to restore an older one, as mixing wallets " +
                            "together can lead to invalidated backups.\n\n" +
                            "Please finalize your trades, close all your open offers and go to the Funds section to withdraw your bitcoin.\n" +
                            "In case you cannot access your bitcoin you can use the emergency tool to empty the wallet.\n" +
                            "To open that emergency tool press \"cmd + e\".")
                    .actionButtonText("I want to restore anyway")
                    .onAction(this::checkIfEncrypted)
                    .closeButtonText("I will empty my wallet first")
                    .show();
        } else {
            checkIfEncrypted();
        }
    }

    private void checkIfEncrypted() {
        if (walletsManager.areWalletsEncrypted()) {
            new Popup()
                    .information("Your bitcoin wallet is encrypted.\n\n" +
                            "After restore, the wallet will no longer be encrypted and you must set a new password.\n\n" +
                            "Do you want to proceed?")
                    .closeButtonText("No")
                    .actionButtonText("Yes")
                    .onAction(this::doRestore)
                    .show();
        } else {
            doRestore();
        }
    }

    private void doRestore() {
        long date = restoreDatePicker.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        DeterministicSeed btcSeed = new DeterministicSeed(Splitter.on(" ").splitToList(btcSeedWordsTextArea.getText()), null, "", date);
        DeterministicSeed squSeed = new DeterministicSeed(Splitter.on(" ").splitToList(squSeedWordsTextArea.getText()), null, "", date);
        walletsManager.restoreSeedWords(
                btcSeed,
                squSeed,
                () -> UserThread.execute(() -> {
                    log.debug("Wallet restored with seed words");

                    new Popup()
                            .feedback("Wallet restored successfully with the new seed words.\n\n" +
                                    "You need to shut down and restart the application.")
                            .closeButtonText("Shut down")
                            .onClose(BitsquareApp.shutDownHandler::run)
                            .show();
                }),
                throwable -> UserThread.execute(() -> {
                    log.error(throwable.getMessage());
                    new Popup()
                            .error("An error occurred when restoring the wallet with seed words.\n" +
                                    "Error message: " + throwable.getMessage())
                            .show();
                }));
    }
}
