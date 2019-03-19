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

package bisq.desktop.main.account.content.seedwords;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.WalletPasswordWindow;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.storage.Storage;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.wallet.DeterministicSeed;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.io.File;
import java.io.IOException;

import java.util.List;

import static bisq.desktop.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createBooleanBinding;

@FxmlView
public class SeedWordsView extends ActivatableView<GridPane, Void> {
    private final WalletsManager walletsManager;
    private final BtcWalletService btcWalletService;
    private final WalletPasswordWindow walletPasswordWindow;
    private final File storageDir;

    private Button restoreButton;
    private TextArea displaySeedWordsTextArea, seedWordsTextArea;
    private DatePicker datePicker, restoreDatePicker;

    private int gridRow = 0;
    private ChangeListener<Boolean> seedWordsValidChangeListener;
    private final SimpleBooleanProperty seedWordsValid = new SimpleBooleanProperty(false);
    private ChangeListener<String> seedWordsTextAreaChangeListener;
    private final BooleanProperty seedWordsEdited = new SimpleBooleanProperty();
    private String seedWordText;
    private LocalDate walletCreationDate;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SeedWordsView(WalletsManager walletsManager,
                          BtcWalletService btcWalletService,
                          WalletPasswordWindow walletPasswordWindow,
                          @Named(Storage.STORAGE_DIR) File storageDir) {
        this.walletsManager = walletsManager;
        this.btcWalletService = btcWalletService;
        this.walletPasswordWindow = walletPasswordWindow;
        this.storageDir = storageDir;
    }

    @Override
    protected void initialize() {
        addTitledGroupBg(root, gridRow, 2, Res.get("account.seed.backup.title"));
        displaySeedWordsTextArea = addTopLabelTextArea(root, gridRow, Res.get("seed.seedWords"), "", Layout.FIRST_ROW_DISTANCE).second;
        displaySeedWordsTextArea.getStyleClass().add("wallet-seed-words");
        displaySeedWordsTextArea.setPrefHeight(40);
        displaySeedWordsTextArea.setMaxHeight(40);
        displaySeedWordsTextArea.setEditable(false);

        datePicker = addTopLabelDatePicker(root, ++gridRow, Res.get("seed.date"), 10).second;
        datePicker.setMouseTransparent(true);

        addTitledGroupBg(root, ++gridRow, 3, Res.get("seed.restore.title"), Layout.GROUP_DISTANCE);
        seedWordsTextArea = addTopLabelTextArea(root, gridRow, Res.get("seed.seedWords"), "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        seedWordsTextArea.getStyleClass().add("wallet-seed-words");
        seedWordsTextArea.setPrefHeight(40);
        seedWordsTextArea.setMaxHeight(40);

        restoreDatePicker = addTopLabelDatePicker(root, ++gridRow, Res.get("seed.date"), 10).second;
        restoreButton = addPrimaryActionButtonAFterGroup(root, ++gridRow, Res.get("seed.restore"));

        addTitledGroupBg(root, ++gridRow, 1, Res.get("shared.information"), Layout.GROUP_DISTANCE);
        addMultilineLabel(root, gridRow, Res.get("account.seed.info"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        seedWordsValidChangeListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                seedWordsTextArea.getStyleClass().remove("validation-error");
            } else {
                seedWordsTextArea.getStyleClass().add("validation-error");
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
    }

    @Override
    public void activate() {
        seedWordsValid.addListener(seedWordsValidChangeListener);
        seedWordsTextArea.textProperty().addListener(seedWordsTextAreaChangeListener);
        restoreButton.disableProperty().bind(createBooleanBinding(() -> !seedWordsValid.get() || !seedWordsEdited.get(),
                seedWordsValid, seedWordsEdited));

        restoreButton.setOnAction(e -> {
            new Popup<>().information(Res.get("account.seed.restore.info"))
                    .closeButtonText(Res.get("shared.cancel"))
                    .actionButtonText(Res.get("account.seed.restore.ok"))
                    .onAction(this::onRestore)
                    .show();
        });

        seedWordsTextArea.getStyleClass().remove("validation-error");
        restoreDatePicker.getStyleClass().remove("validation-error");


        DeterministicSeed keyChainSeed = btcWalletService.getKeyChainSeed();
        // wallet creation date is not encrypted
        walletCreationDate = Instant.ofEpochSecond(walletsManager.getChainSeedCreationTimeSeconds()).atZone(ZoneId.systemDefault()).toLocalDate();
        if (keyChainSeed.isEncrypted()) {
            askForPassword();
        } else {
            String key = "showSeedWordsWarning";
            if (DontShowAgainLookup.showAgain(key)) {
                new Popup<>().warning(Res.get("account.seed.warn.noPw.msg"))
                        .actionButtonText(Res.get("account.seed.warn.noPw.yes"))
                        .onAction(() -> {
                            DontShowAgainLookup.dontShowAgain(key, true);
                            initSeedWords(keyChainSeed);
                            showSeedScreen();
                        })
                        .closeButtonText(Res.get("shared.no"))
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
        seedWordsTextArea.textProperty().removeListener(seedWordsTextAreaChangeListener);
        restoreButton.disableProperty().unbind();
        restoreButton.setOnAction(null);

        displaySeedWordsTextArea.setText("");
        seedWordsTextArea.setText("");

        restoreDatePicker.setValue(null);
        datePicker.setValue(null);

        seedWordsTextArea.getStyleClass().remove("validation-error");
        restoreDatePicker.getStyleClass().remove("validation-error");
    }

    private void askForPassword() {
        walletPasswordWindow.headLine(Res.get("account.seed.enterPw")).onAesKey(aesKey -> {
            initSeedWords(walletsManager.getDecryptedSeed(aesKey, btcWalletService.getKeyChainSeed(), btcWalletService.getKeyCrypter()));
            showSeedScreen();
        }).hideForgotPasswordButton().show();
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
        if (walletsManager.hasPositiveBalance()) {
            new Popup<>().warning(Res.get("seed.warn.walletNotEmpty.msg"))
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
            new Popup<>().information(Res.get("seed.warn.notEncryptedAnymore"))
                    .closeButtonText(Res.get("shared.no"))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(this::doRestore)
                    .show();
        } else {
            doRestore();
        }
    }

    private void doRestore() {
        LocalDate value = restoreDatePicker.getValue();
        if (value == null) {
            // If no date was specified, use Bisq 0.5 release date (no current Bisq wallet could have been created before that date).
            value = LocalDate.of(2017, Month.JUNE, 28);
        }

        // We subtract 1 day to be sure to not have any issues with timezones. Even if we can be sure that the timezone
        // is handled correctly it could be that the user created the wallet in one timezone and make a restore at
        // a different timezone which could lead in the worst case that he miss the first day of the wallet transactions.
        LocalDateTime localDateTime = value.atStartOfDay().minusDays(1);
        long date = localDateTime.toEpochSecond(ZoneOffset.UTC);

        DeterministicSeed seed = new DeterministicSeed(Splitter.on(" ").splitToList(seedWordsTextArea.getText()), null, "", date);
        GUIUtil.restoreSeedWords(seed, walletsManager, storageDir);
    }
}
