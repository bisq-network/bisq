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

package io.bitsquare.gui.main.account.content.seedwords;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.WalletPasswordWindow;
import io.bitsquare.gui.util.Layout;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.wallet.DeterministicSeed;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static io.bitsquare.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createBooleanBinding;

@FxmlView
public class SeedWordsView extends ActivatableView<GridPane, Void> {
    private final WalletService walletService;
    private final WalletPasswordWindow walletPasswordWindow;

    private Button restoreButton;
    private TextArea seedWordsTextArea;
    private DatePicker datePicker;

    private int gridRow = 0;
    private DeterministicSeed keyChainSeed;
    private ChangeListener<Boolean> seedWordsValidChangeListener;
    private SimpleBooleanProperty seedWordsValid;
    private SimpleBooleanProperty dateValid;
    private ChangeListener<String> seedWordsTextAreaChangeListener;
    private ChangeListener<Boolean> datePickerChangeListener;
    private ChangeListener<LocalDate> dateChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SeedWordsView(WalletService walletService, WalletPasswordWindow walletPasswordWindow) {
        this.walletService = walletService;
        this.walletPasswordWindow = walletPasswordWindow;
    }

    @Override
    protected void initialize() {
        addTitledGroupBg(root, gridRow, 3, "Backup or restore your wallet seed words");
        seedWordsTextArea = addLabelTextArea(root, gridRow, "Wallet seed words:", "", Layout.FIRST_ROW_DISTANCE).second;
        seedWordsTextArea.setPrefHeight(60);
        datePicker = addLabelDatePicker(root, ++gridRow, "Creation Date:").second;
        restoreButton = addButton(root, ++gridRow, "Restore wallet");

        addTitledGroupBg(root, ++gridRow, 1, "Information", Layout.GROUP_DISTANCE);
        addMultilineLabel(root, gridRow, "Please write down you wallet seed words and the creation date.\n" +
                        "You can recover your wallet with those words and the date in emergency case.",
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
    }

    @Override
    public void activate() {
        seedWordsTextArea.getStyleClass().remove("validation_error");
        datePicker.getStyleClass().remove("validation_error");

        DeterministicSeed keyChainSeed = walletService.getWallet().getKeyChainSeed();
        if (keyChainSeed.isEncrypted()) {
            restoreButton.setDisable(true);
            seedWordsTextArea.setDisable(true);
            datePicker.setDisable(true);
            askForPassword();
        } else {
            showSeedScreen(keyChainSeed);
        }
    }

    @Override
    protected void deactivate() {
        seedWordsValid.removeListener(seedWordsValidChangeListener);
        seedWordsTextArea.textProperty().removeListener(seedWordsTextAreaChangeListener);
        dateValid.removeListener(datePickerChangeListener);
        datePicker.valueProperty().removeListener(dateChangeListener);

        seedWordsTextArea.setText("");
        datePicker.setValue(null);
        restoreButton.disableProperty().unbind();
        seedWordsTextArea.getStyleClass().remove("validation_error");
        datePicker.getStyleClass().remove("validation_error");
    }


    private void askForPassword() {
        walletPasswordWindow.onAesKey(aesKey -> {
            Wallet wallet = walletService.getWallet();
            KeyCrypter keyCrypter = wallet.getKeyCrypter();
            keyChainSeed = wallet.getKeyChainSeed();
            if (keyCrypter != null) {
                DeterministicSeed decryptedSeed = keyChainSeed.decrypt(keyCrypter, "", aesKey);
                showSeedScreen(decryptedSeed);
            } else {
                log.warn("keyCrypter is null");
            }
        }).show();
    }

    private void showSeedScreen(DeterministicSeed seed) {
        seedWordsTextArea.setDisable(false);
        datePicker.setDisable(false);
        List<String> mnemonicCode = seed.getMnemonicCode();
        if (mnemonicCode != null)
            seedWordsTextArea.setText(Joiner.on(" ").join(mnemonicCode));
        LocalDate creationDate = Instant.ofEpochSecond(seed.getCreationTimeSeconds()).atZone(ZoneId.systemDefault()).toLocalDate();
        datePicker.setValue(creationDate);
        restoreButton.setOnAction(e -> onRestore());

        BooleanProperty seedWordsEdited = new SimpleBooleanProperty();

        seedWordsValid = new SimpleBooleanProperty(true);
        seedWordsValidChangeListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                seedWordsTextArea.getStyleClass().remove("validation_error");
            } else {
                seedWordsTextArea.getStyleClass().add("validation_error");
            }
        };
        seedWordsValid.addListener(seedWordsValidChangeListener);

        seedWordsTextAreaChangeListener = (observable, oldValue, newValue) -> {
            seedWordsEdited.set(true);
            try {
                MnemonicCode codec = new MnemonicCode();
                codec.check(Splitter.on(" ").splitToList(newValue));
                seedWordsValid.set(true);
            } catch (IOException | MnemonicException e) {
                seedWordsValid.set(false);
            }

            if (creationDate.equals(datePicker.getValue()))
                datePicker.setValue(null);
        };
        seedWordsTextArea.textProperty().addListener(seedWordsTextAreaChangeListener);

        dateValid = new SimpleBooleanProperty(true);
        datePickerChangeListener = (observable, oldValue, newValue) -> {
            if (newValue)
                datePicker.getStyleClass().remove("validation_error");
            else
                datePicker.getStyleClass().add("validation_error");
        };
        dateValid.addListener(datePickerChangeListener);

        dateChangeListener = (observable, oldValue, newValue) -> {
            dateValid.set(newValue != null && !newValue.isAfter(LocalDate.now()));
        };
        datePicker.valueProperty().addListener(dateChangeListener);

        restoreButton.disableProperty().bind(createBooleanBinding(() -> !seedWordsValid.get() || !dateValid.get() || !seedWordsEdited.get(),
                seedWordsValid, dateValid, seedWordsEdited));
    }

    private void onRestore() {
        Wallet wallet = walletService.getWallet();
        if (wallet.getBalance(Wallet.BalanceType.AVAILABLE).value > 0) {
            new Popup()
                    .warning("Your bitcoin wallet is not empty.\n\n" +
                            "You must empty this wallet before attempting to restore an older one, as mixing wallets " +
                            "together can lead to invalidated backups.\n\n" +
                            "Please finalize your trades, close all your open offers and go to the Funds section to withdraw your bitcoin.\n" +
                            "In case you cannot access your bitcoin you can use the emergency tool to empty the wallet.\n" +
                            "To open that emergency tool press \"cmd + e\".")
                    .show();
        } else if (wallet.isEncrypted()) {
            new Popup()
                    .warning("Your bitcoin wallet is encrypted.\n\n" +
                            "After restore, the wallet will no longer be encrypted and you must set a new password.")
                    .closeButtonText("I understand")
                    .onClose(() -> doRestore()).show();
        } else {
            doRestore();
        }
    }

    private void doRestore() {
        log.info("Attempting wallet restore using seed '{}' from date {}", seedWordsTextArea.getText(), datePicker.getValue());
        long date = datePicker.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        DeterministicSeed seed = new DeterministicSeed(Splitter.on(" ").splitToList(seedWordsTextArea.getText()), null, "", date);
        walletService.restoreSeedWords(seed,
                () -> UserThread.execute(() -> {
                    log.debug("Wallet restored with seed words");

                    new Popup()
                            .feedback("Wallet restored successfully with the new seed words.\n\n" +
                                    "You need to shut down and restart the application.")
                            .closeButtonText("Shut down")
                            .onClose(() -> BitsquareApp.shutDownHandler.run()).show();
                    //TODO
                   /* new Popup()
                            .information("Wallet restored successfully with the new seed words.\n\n" +
                                    "You need to restart now the application.")
                            .closeButtonText("Restart")
                            .onClose(() -> BitsquareApp.restartDownHandler.run()).show();*/
                }),
                throwable -> UserThread.execute(() -> {
                    log.error(throwable.getMessage());
                    new Popup()
                            .warning("You entered the wrong password.\n\n" +
                                    "Please try entering your password again, carefully checking for typos or spelling errors.")
                            .show();

                    new Popup()
                            .error("An error occurred when restoring the wallet with seed words.\n" +
                                    "Error message: " + throwable.getMessage())
                            .show();
                }));
    }
}