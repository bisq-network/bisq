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
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.btc.wallet.WalletsManager;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.WalletPasswordWindow;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.messages.user.Preferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
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
    private final WalletsManager walletsManager;
    private final BtcWalletService btcWalletService;
    private final SquWalletService squWalletService;
    private final WalletPasswordWindow walletPasswordWindow;
    private final Preferences preferences;

    private Button restoreButton;
    private TextArea displayBtcSeedWordsTextArea, displaySquSeedWordsTextArea, btcSeedWordsTextArea, squSeedWordsTextArea;
    private DatePicker datePicker, restoreDatePicker;

    private int gridRow = 0;
    private ChangeListener<Boolean> btcSeedWordsValidChangeListener, squSeedWordsValidChangeListener;
    private final SimpleBooleanProperty btcSeedWordsValid = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty squSeedWordsValid = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty dateValid = new SimpleBooleanProperty(false);
    private ChangeListener<String> btcSeedWordsTextAreaChangeListener;
    private ChangeListener<String> squSeedWordsTextAreaChangeListener;
    private ChangeListener<Boolean> datePickerChangeListener;
    private ChangeListener<LocalDate> dateChangeListener;
    private final BooleanProperty btcSeedWordsEdited = new SimpleBooleanProperty();
    private final BooleanProperty squSeedWordsEdited = new SimpleBooleanProperty();
    private String btcSeedWordText;
    private String squSeedWordText;
    private LocalDate walletCreationDate;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SeedWordsView(WalletsManager walletsManager, BtcWalletService btcWalletService, SquWalletService squWalletService, WalletPasswordWindow walletPasswordWindow, Preferences preferences) {
        this.walletsManager = walletsManager;
        this.btcWalletService = btcWalletService;
        this.squWalletService = squWalletService;
        this.walletPasswordWindow = walletPasswordWindow;
        this.preferences = preferences;
    }

    @Override
    protected void initialize() {
        addTitledGroupBg(root, gridRow, 3, "Backup your wallet seed words");
        displayBtcSeedWordsTextArea = addLabelTextArea(root, gridRow, "BTC wallet seed words:", "", Layout.FIRST_ROW_DISTANCE).second;
        displayBtcSeedWordsTextArea.setPrefHeight(60);
        displayBtcSeedWordsTextArea.setEditable(false);

        displaySquSeedWordsTextArea = addLabelTextArea(root, ++gridRow, "SQU wallet seed words:", "").second;
        displaySquSeedWordsTextArea.setPrefHeight(60);
        displaySquSeedWordsTextArea.setEditable(false);

        datePicker = addLabelDatePicker(root, ++gridRow, "Wallet Date:").second;
        datePicker.setMouseTransparent(true);

        addTitledGroupBg(root, ++gridRow, 3, "Restore your wallet seed words", Layout.GROUP_DISTANCE);
        btcSeedWordsTextArea = addLabelTextArea(root, gridRow, "BTC wallet seed words:", "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        btcSeedWordsTextArea.setPrefHeight(60);

        squSeedWordsTextArea = addLabelTextArea(root, ++gridRow, "SQU wallet seed words:", "").second;
        squSeedWordsTextArea.setPrefHeight(60);

        restoreDatePicker = addLabelDatePicker(root, ++gridRow, "Wallet Date:").second;
        restoreButton = addButtonAfterGroup(root, ++gridRow, "Restore wallet");

        addTitledGroupBg(root, ++gridRow, 1, "Information", Layout.GROUP_DISTANCE);
        addMultilineLabel(root, gridRow, "Please write down both wallet seed words and the date! " +
                        "You can recover your wallet any time with those seed words and the date.",
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        btcSeedWordsValidChangeListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                btcSeedWordsTextArea.getStyleClass().remove("validation_error");
            } else {
                btcSeedWordsTextArea.getStyleClass().add("validation_error");
            }
        };

        squSeedWordsValidChangeListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                squSeedWordsTextArea.getStyleClass().remove("validation_error");
            } else {
                squSeedWordsTextArea.getStyleClass().add("validation_error");
            }
        };

        btcSeedWordsTextAreaChangeListener = (observable, oldValue, newValue) -> {
            btcSeedWordsEdited.set(true);
            try {
                MnemonicCode codec = new MnemonicCode();
                codec.check(Splitter.on(" ").splitToList(newValue));
                btcSeedWordsValid.set(true);
            } catch (IOException | MnemonicException e) {
                btcSeedWordsValid.set(false);
            }
        };

        squSeedWordsTextAreaChangeListener = (observable, oldValue, newValue) -> {
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

        dateChangeListener = (observable, oldValue, newValue) -> dateValid.set(true);
    }

    @Override
    public void activate() {
        btcSeedWordsValid.addListener(btcSeedWordsValidChangeListener);
        squSeedWordsValid.addListener(squSeedWordsValidChangeListener);
        dateValid.addListener(datePickerChangeListener);
        btcSeedWordsTextArea.textProperty().addListener(btcSeedWordsTextAreaChangeListener);
        squSeedWordsTextArea.textProperty().addListener(squSeedWordsTextAreaChangeListener);
        restoreDatePicker.valueProperty().addListener(dateChangeListener);
        restoreButton.disableProperty().bind(createBooleanBinding(() -> !btcSeedWordsValid.get() || !squSeedWordsValid.get() || !dateValid.get() || !btcSeedWordsEdited.get() || !squSeedWordsEdited.get(),
                btcSeedWordsValid, squSeedWordsValid, dateValid, btcSeedWordsEdited, squSeedWordsEdited));

        restoreButton.setOnAction(e -> onRestore());

        btcSeedWordsTextArea.getStyleClass().remove("validation_error");
        squSeedWordsTextArea.getStyleClass().remove("validation_error");
        restoreDatePicker.getStyleClass().remove("validation_error");


        DeterministicSeed btcKeyChainSeed = btcWalletService.getKeyChainSeed();
        DeterministicSeed squKeyChainSeed = squWalletService.getKeyChainSeed();
        // wallet creation date is not encrypted
        walletCreationDate = Instant.ofEpochSecond(walletsManager.getChainSeedCreationTimeSeconds()).atZone(ZoneId.systemDefault()).toLocalDate();
        if (btcKeyChainSeed.isEncrypted()) {
            askForPassword();
        } else {
            String key = "showSeedWordsWarning";
            if (preferences.showAgain(key)) {
                new Popup().warning("You have not setup a wallet password which would protect the display of the seed words.\n\n" +
                        "Do you want to display the seed words?")
                        .actionButtonText("Yes, and don't ask me again")
                        .onAction(() -> {
                            preferences.dontShowAgain(key, true);
                            initBtcSeedWords(btcKeyChainSeed);
                            initSquSeedWords(squKeyChainSeed);
                            showSeedScreen();
                        })
                        .closeButtonText("No")
                        .show();
            } else {
                initBtcSeedWords(btcKeyChainSeed);
                initSquSeedWords(squKeyChainSeed);
                showSeedScreen();
            }
        }
    }

    @Override
    protected void deactivate() {
        btcSeedWordsValid.removeListener(btcSeedWordsValidChangeListener);
        squSeedWordsValid.removeListener(squSeedWordsValidChangeListener);
        dateValid.removeListener(datePickerChangeListener);
        btcSeedWordsTextArea.textProperty().removeListener(btcSeedWordsTextAreaChangeListener);
        squSeedWordsTextArea.textProperty().removeListener(squSeedWordsTextAreaChangeListener);
        restoreDatePicker.valueProperty().removeListener(dateChangeListener);
        restoreButton.disableProperty().unbind();
        restoreButton.setOnAction(null);

        displayBtcSeedWordsTextArea.setText("");
        displaySquSeedWordsTextArea.setText("");
        btcSeedWordsTextArea.setText("");
        squSeedWordsTextArea.setText("");

        restoreDatePicker.setValue(null);
        datePicker.setValue(null);

        btcSeedWordsTextArea.getStyleClass().remove("validation_error");
        squSeedWordsTextArea.getStyleClass().remove("validation_error");
        restoreDatePicker.getStyleClass().remove("validation_error");
    }

    private void askForPassword() {
        walletPasswordWindow.headLine("Enter password to view seed words").onAesKey(aesKey -> {
            initBtcSeedWords(walletsManager.getDecryptedSeed(aesKey, btcWalletService.getKeyChainSeed(), btcWalletService.getKeyCrypter()));
            initSquSeedWords(walletsManager.getDecryptedSeed(aesKey, squWalletService.getKeyChainSeed(), squWalletService.getKeyCrypter()));
            showSeedScreen();
        }).show();
    }

    private void initBtcSeedWords(DeterministicSeed seed) {
        List<String> mnemonicCode = seed.getMnemonicCode();
        if (mnemonicCode != null) {
            btcSeedWordText = Joiner.on(" ").join(mnemonicCode);
        }
    }

    private void initSquSeedWords(DeterministicSeed seed) {
        List<String> mnemonicCode = seed.getMnemonicCode();
        if (mnemonicCode != null) {
            squSeedWordText = Joiner.on(" ").join(mnemonicCode);
        }
    }

    private void showSeedScreen() {
        displayBtcSeedWordsTextArea.setText(btcSeedWordText);
        displaySquSeedWordsTextArea.setText(squSeedWordText);
        datePicker.setValue(walletCreationDate);
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
                    .closeButtonText("I will empty my wallets first")
                    .show();
        } else {
            checkIfEncrypted();
        }
    }

    private void checkIfEncrypted() {
        if (walletsManager.areWalletsEncrypted()) {
            new Popup()
                    .information("Your wallets are encrypted.\n\n" +
                            "After restore, the wallets will no longer be encrypted and you must set a new password.\n\n" +
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
                    log.info("Wallets restored with seed words");

                    new Popup()
                            .feedback("Wallets restored successfully with the new seed words.\n\n" +
                                    "You need to shut down and restart the application.")
                            .closeButtonText("Shut down")
                            .onClose(BitsquareApp.shutDownHandler::run).show();
                }),
                throwable -> UserThread.execute(() -> {
                    log.error(throwable.getMessage());
                    new Popup()
                            .error("An error occurred when restoring the wallets with seed words.\n" +
                                    "Error message: " + throwable.getMessage())
                            .show();
                }));
    }
}