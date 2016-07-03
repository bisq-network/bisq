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
import io.bitsquare.user.Preferences;
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
    private Preferences preferences;

    private Button restoreButton;
    private TextArea displaySeedWordsTextArea, restoreSeedWordsTextArea;
    private DatePicker datePicker, restoreDatePicker;

    private int gridRow = 0;
    private DeterministicSeed keyChainSeed;
    private ChangeListener<Boolean> seedWordsValidChangeListener;
    private SimpleBooleanProperty seedWordsValid = new SimpleBooleanProperty(false);
    private SimpleBooleanProperty dateValid = new SimpleBooleanProperty(false);
    private ChangeListener<String> seedWordsTextAreaChangeListener;
    private ChangeListener<Boolean> datePickerChangeListener;
    private ChangeListener<LocalDate> dateChangeListener;
    private BooleanProperty seedWordsEdited = new SimpleBooleanProperty();
    private String seedWordText;
    private LocalDate walletCreationDate;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SeedWordsView(WalletService walletService, WalletPasswordWindow walletPasswordWindow, Preferences preferences) {
        this.walletService = walletService;
        this.walletPasswordWindow = walletPasswordWindow;
        this.preferences = preferences;
    }

    @Override
    protected void initialize() {
        addTitledGroupBg(root, gridRow, 2, "Backup your wallet seed words");
        displaySeedWordsTextArea = addLabelTextArea(root, gridRow, "Wallet seed words:", "", Layout.FIRST_ROW_DISTANCE).second;
        displaySeedWordsTextArea.setPrefHeight(60);
        displaySeedWordsTextArea.setEditable(false);
        datePicker = addLabelDatePicker(root, ++gridRow, "Wallet Date:").second;
        datePicker.setMouseTransparent(true);

        addTitledGroupBg(root, ++gridRow, 2, "Restore your wallet seed words", Layout.GROUP_DISTANCE);
        restoreSeedWordsTextArea = addLabelTextArea(root, gridRow, "Wallet seed words:", "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        restoreSeedWordsTextArea.setPrefHeight(60);
        restoreDatePicker = addLabelDatePicker(root, ++gridRow, "Wallet Date:").second;
        restoreButton = addButtonAfterGroup(root, ++gridRow, "Restore wallet");

        addTitledGroupBg(root, ++gridRow, 1, "Information", Layout.GROUP_DISTANCE);
        addMultilineLabel(root, gridRow, "Please write down you wallet seed words.\n" +
                        "You can recover your wallet with those seed words.\n" +
                        "Please note that the wallet date is the date of the wallet into which you want to restore!",
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);


        seedWordsValidChangeListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                restoreSeedWordsTextArea.getStyleClass().remove("validation_error");
            } else {
                restoreSeedWordsTextArea.getStyleClass().add("validation_error");
            }
        };

        seedWordsTextAreaChangeListener = (observable, oldValue, newValue) -> {
            seedWordsEdited.set(true);
            try {
                MnemonicCode codec = new MnemonicCode();
                codec.check(Splitter.on(" ").splitToList(newValue));
                seedWordsValid.set(true);
            } catch (IOException | MnemonicException e) {
                seedWordsValid.set(false);
            }
        };


        datePickerChangeListener = (observable, oldValue, newValue) -> {
            if (newValue)
                restoreDatePicker.getStyleClass().remove("validation_error");
            else
                restoreDatePicker.getStyleClass().add("validation_error");
        };

        dateChangeListener = (observable, oldValue, newValue) -> {
            dateValid.set(walletCreationDate.equals(newValue));
        };
    }

    @Override
    public void activate() {
        seedWordsValid.addListener(seedWordsValidChangeListener);
        dateValid.addListener(datePickerChangeListener);
        restoreSeedWordsTextArea.textProperty().addListener(seedWordsTextAreaChangeListener);
        restoreDatePicker.valueProperty().addListener(dateChangeListener);
        restoreButton.disableProperty().bind(createBooleanBinding(() -> !seedWordsValid.get() || !dateValid.get() || !seedWordsEdited.get(),
                seedWordsValid, dateValid, seedWordsEdited));

        restoreButton.setOnAction(e -> onRestore());

        restoreSeedWordsTextArea.getStyleClass().remove("validation_error");
        restoreDatePicker.getStyleClass().remove("validation_error");


        DeterministicSeed keyChainSeed = walletService.getWallet().getKeyChainSeed();
        // wallet creation date is not encrypted
        walletCreationDate = Instant.ofEpochSecond(keyChainSeed.getCreationTimeSeconds()).atZone(ZoneId.systemDefault()).toLocalDate();
        if (keyChainSeed.isEncrypted()) {
            askForPassword();
        } else {
            String key = "showSeedWordsWarning";
            if (preferences.showAgain(key)) {
                new Popup().warning("You have not setup a wallet password which would protect the display of the seed words.\n\n" +
                        "Do you want to display the seed words?")
                        .actionButtonText("Yes, and don't ask me again")
                        .onAction(() -> {
                            preferences.dontShowAgain(key, true);
                            initSeedWords(keyChainSeed);
                            showSeedScreen();
                        })
                        .closeButtonText("No")
                        .show();
            } else {
                initSeedWords(keyChainSeed);
                showSeedScreen();
            }
        }
    }

    @Override
    protected void deactivate() {
        seedWordsValid.removeListener(seedWordsValidChangeListener);
        dateValid.removeListener(datePickerChangeListener);
        restoreSeedWordsTextArea.textProperty().removeListener(seedWordsTextAreaChangeListener);
        restoreDatePicker.valueProperty().removeListener(dateChangeListener);
        restoreButton.disableProperty().unbind();

        restoreButton.setOnAction(null);


        displaySeedWordsTextArea.setText("");
        restoreSeedWordsTextArea.setText("");

        restoreDatePicker.setValue(null);
        datePicker.setValue(null);

        restoreSeedWordsTextArea.getStyleClass().remove("validation_error");
        restoreDatePicker.getStyleClass().remove("validation_error");
    }

    private void askForPassword() {
        walletPasswordWindow.headLine("Enter password to view seed words").onAesKey(aesKey -> {
            Wallet wallet = walletService.getWallet();
            KeyCrypter keyCrypter = wallet.getKeyCrypter();
            keyChainSeed = wallet.getKeyChainSeed();
            if (keyCrypter != null) {
                DeterministicSeed decryptedSeed = keyChainSeed.decrypt(keyCrypter, "", aesKey);
                initSeedWords(decryptedSeed);
                showSeedScreen();
            } else {
                log.warn("keyCrypter is null");
            }
        }).show();
    }

    private void initSeedWords(DeterministicSeed seed) {
        List<String> mnemonicCode = seed.getMnemonicCode();
        if (mnemonicCode != null) {
            seedWordText = Joiner.on(" ").join(mnemonicCode);
        }
    }

    private void showSeedScreen() {
        displaySeedWordsTextArea.setText(seedWordText);
        datePicker.setValue(walletCreationDate);
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
                    .actionButtonText("I want to restore anyway")
                    .onAction(this::checkIfEncrypted)
                    .closeButtonText("I will empty my wallet first")
                    .show();
        } else {
            checkIfEncrypted();
        }
    }

    private void checkIfEncrypted() {
        if (walletService.getWallet().isEncrypted()) {
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
        log.info("Attempting wallet restore using seed '{}' from date {}", restoreSeedWordsTextArea.getText(), restoreDatePicker.getValue());
        long date = restoreDatePicker.getValue().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        DeterministicSeed seed = new DeterministicSeed(Splitter.on(" ").splitToList(restoreSeedWordsTextArea.getText()), null, "", date);
        walletService.restoreSeedWords(seed,    
                () -> UserThread.execute(() -> {
                    log.debug("Wallet restored with seed words");

                    new Popup()
                            .feedback("Wallet restored successfully with the new seed words.\n\n" +
                                    "You need to shut down and restart the application.")
                            .closeButtonText("Shut down")
                            .onClose(BitsquareApp.shutDownHandler::run).show();
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