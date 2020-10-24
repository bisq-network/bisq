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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.PasswordTextField;
import bisq.desktop.main.SharedPresentation;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;
import bisq.desktop.util.Transitions;

import bisq.core.btc.wallet.WalletsManager;
import bisq.core.crypto.ScryptUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.Tuple2;

import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.wallet.DeterministicSeed;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.Splitter;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;

import org.bouncycastle.crypto.params.KeyParameter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addPasswordTextField;
import static bisq.desktop.util.FormBuilder.addPrimaryActionButton;
import static bisq.desktop.util.FormBuilder.addTextArea;
import static bisq.desktop.util.FormBuilder.addTopLabelDatePicker;
import static com.google.common.base.Preconditions.checkArgument;
import static javafx.beans.binding.Bindings.createBooleanBinding;

@Slf4j
public class WalletPasswordWindow extends Overlay<WalletPasswordWindow> {
    private final WalletsManager walletsManager;
    private final OpenOfferManager openOfferManager;
    private File storageDir;

    private Button unlockButton;
    private AesKeyHandler aesKeyHandler;
    private PasswordTextField passwordTextField;
    private Button forgotPasswordButton;
    private Button restoreButton;
    private TextArea seedWordsTextArea;
    private DatePicker datePicker;
    private final SimpleBooleanProperty seedWordsValid = new SimpleBooleanProperty(false);
    private final BooleanProperty seedWordsEdited = new SimpleBooleanProperty();
    private ChangeListener<String> changeListener;
    private ChangeListener<String> wordsTextAreaChangeListener;
    private ChangeListener<Boolean> seedWordsValidChangeListener;
    private boolean hideForgotPasswordButton = false;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface AesKeyHandler {
        void onAesKey(KeyParameter aesKey);
    }

    @Inject
    private WalletPasswordWindow(WalletsManager walletsManager,
                                 OpenOfferManager openOfferManager,
                                 @Named(Config.STORAGE_DIR) File storageDir) {
        this.walletsManager = walletsManager;
        this.openOfferManager = openOfferManager;
        this.storageDir = storageDir;
        type = Type.Attention;
        width = 900;
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
            headLine = Res.get("walletPasswordWindow.headline");

        createGridPane();
        addHeadLine();
        addInputFields();
        addButtons();
        applyStyles();
        display();
    }

    public WalletPasswordWindow onAesKey(AesKeyHandler aesKeyHandler) {
        this.aesKeyHandler = aesKeyHandler;
        return this;
    }

    public WalletPasswordWindow hideForgotPasswordButton() {
        this.hideForgotPasswordButton = true;
        return this;
    }

    @Override
    protected void cleanup() {
        if (passwordTextField != null)
            passwordTextField.textProperty().removeListener(changeListener);

        if (seedWordsValidChangeListener != null) {
            seedWordsValid.removeListener(seedWordsValidChangeListener);
            seedWordsTextArea.textProperty().removeListener(wordsTextAreaChangeListener);
            restoreButton.disableProperty().unbind();
            restoreButton.setOnAction(null);
            seedWordsTextArea.setText("");
            datePicker.setValue(null);
            seedWordsTextArea.getStyleClass().remove("validation-error");
            datePicker.getStyleClass().remove("validation-error");
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
        passwordTextField = addPasswordTextField(gridPane, ++rowIndex, Res.get("password.enterPassword"), Layout.FLOATING_LABEL_DISTANCE);
        GridPane.setColumnSpan(passwordTextField, 1);
        GridPane.setHalignment(passwordTextField, HPos.LEFT);
        changeListener = (observable, oldValue, newValue) -> unlockButton.setDisable(!passwordTextField.validate());
        passwordTextField.textProperty().addListener(changeListener);
    }

    @Override
    protected void addButtons() {
        BusyAnimation busyAnimation = new BusyAnimation(false);
        Label deriveStatusLabel = new AutoTooltipLabel();

        unlockButton = new AutoTooltipButton(Res.get("shared.unlock"));
        unlockButton.setDefaultButton(true);
        unlockButton.getStyleClass().add("action-button");
        unlockButton.setDisable(true);
        unlockButton.setOnAction(e -> {
            String password = passwordTextField.getText();
            checkArgument(password.length() < 500, Res.get("password.tooLong"));
            KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
            if (keyCrypterScrypt != null) {
                busyAnimation.play();
                deriveStatusLabel.setText(Res.get("password.deriveKey"));
                ScryptUtil.deriveKeyWithScrypt(keyCrypterScrypt, password, aesKey -> {
                    if (walletsManager.checkAESKey(aesKey)) {
                        if (aesKeyHandler != null)
                            aesKeyHandler.onAesKey(aesKey);

                        hide();
                    } else {
                        busyAnimation.stop();
                        deriveStatusLabel.setText("");

                        UserThread.runAfter(() -> new Popup()
                                .warning(Res.get("password.wrongPw"))
                                .onClose(this::blurAgain).show(), Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS);
                    }
                });
            } else {
                log.error("wallet.getKeyCrypter() is null, that must not happen.");
            }
        });

        forgotPasswordButton = new AutoTooltipButton(Res.get("password.forgotPassword"));
        forgotPasswordButton.setOnAction(e -> {
            forgotPasswordButton.setDisable(true);
            unlockButton.setDefaultButton(false);
            showRestoreScreen();
        });

        Button cancelButton = new AutoTooltipButton(Res.get("shared.cancel"));
        cancelButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        HBox hBox = new HBox();
        hBox.setMinWidth(560);
        hBox.setPadding(new Insets(0, 0, 0, 0));
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().add(unlockButton);
        if (!hideForgotPasswordButton)
            hBox.getChildren().add(forgotPasswordButton);
        if (!hideCloseButton)
            hBox.getChildren().add(cancelButton);
        hBox.getChildren().addAll(busyAnimation, deriveStatusLabel);
        gridPane.getChildren().add(hBox);


        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.LEFT);
        columnConstraints1.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1);
    }

    private void showRestoreScreen() {
        Label headLine2Label = new AutoTooltipLabel(Res.get("seed.restore.title"));
        headLine2Label.getStyleClass().add("popup-headline");
        headLine2Label.setMouseTransparent(true);
        GridPane.setHalignment(headLine2Label, HPos.LEFT);
        GridPane.setRowIndex(headLine2Label, ++rowIndex);
        GridPane.setMargin(headLine2Label, new Insets(30, 0, 0, 0));
        gridPane.getChildren().add(headLine2Label);

        seedWordsTextArea = addTextArea(gridPane, ++rowIndex, Res.get("seed.enterSeedWords"), 5);
        seedWordsTextArea.setPrefHeight(60);

        Tuple2<Label, DatePicker> labelDatePickerTuple2 = addTopLabelDatePicker(gridPane, ++rowIndex,
                Res.get("seed.creationDate"), 10);
        datePicker = labelDatePickerTuple2.second;
        restoreButton = addPrimaryActionButton(gridPane, ++rowIndex, Res.get("seed.restore"), 0);
        restoreButton.setDefaultButton(true);
        stage.setHeight(570);


        // wallet creation date is not encrypted
        LocalDate walletCreationDate = Instant.ofEpochSecond(walletsManager.getChainSeedCreationTimeSeconds()).atZone(ZoneId.systemDefault()).toLocalDate();
        log.info("walletCreationDate " + walletCreationDate);
        datePicker.setValue(walletCreationDate);
        restoreButton.disableProperty().bind(createBooleanBinding(() -> !seedWordsValid.get() || !seedWordsEdited.get(),
                seedWordsValid, seedWordsEdited));

        seedWordsValidChangeListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                seedWordsTextArea.getStyleClass().remove("validation-error");
            } else {
                seedWordsTextArea.getStyleClass().add("validation-error");
            }
        };

        wordsTextAreaChangeListener = (observable, oldValue, newValue) -> {
            seedWordsEdited.set(true);
            try {
                MnemonicCode codec = new MnemonicCode();
                codec.check(Splitter.on(" ").splitToList(newValue));
                seedWordsValid.set(true);
            } catch (IOException | MnemonicException e) {
                seedWordsValid.set(false);
            }
        };

        seedWordsValid.addListener(seedWordsValidChangeListener);
        seedWordsTextArea.textProperty().addListener(wordsTextAreaChangeListener);
        restoreButton.disableProperty().bind(createBooleanBinding(() -> !seedWordsValid.get() || !seedWordsEdited.get(),
                seedWordsValid, seedWordsEdited));

        restoreButton.setOnAction(e -> onRestore());

        seedWordsTextArea.getStyleClass().remove("validation-error");
        datePicker.getStyleClass().remove("validation-error");

        layout();
    }

    private void onRestore() {
        if (walletsManager.hasPositiveBalance()) {
            new Popup().warning(Res.get("seed.warn.walletNotEmpty.msg"))
                    .actionButtonText(Res.get("seed.warn.walletNotEmpty.restore"))
                    .onAction(this::checkIfEncrypted)
                    .closeButtonText(Res.get("seed.warn.walletNotEmpty.emptyWallet"))
                    .show();
        } else {
            checkIfEncrypted();
        }
    }

    private void checkIfEncrypted() {
        if (walletsManager.areWalletsEncrypted()) {
            new Popup().information(Res.get("seed.warn.notEncryptedAnymore"))
                    .closeButtonText(Res.get("shared.no"))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(this::doRestore)
                    .show();
        } else {
            doRestore();
        }
    }

    private void doRestore() {
        final LocalDate value = datePicker.getValue();
        //TODO Is ZoneOffset correct?
        long date = value != null ? value.atStartOfDay().toEpochSecond(ZoneOffset.UTC) : 0;
        DeterministicSeed seed = new DeterministicSeed(Splitter.on(" ").splitToList(seedWordsTextArea.getText()), null, "", date);
        SharedPresentation.restoreSeedWords(walletsManager, openOfferManager, seed, storageDir);
    }
}
