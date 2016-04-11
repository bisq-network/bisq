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

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.trade.offer.OpenOfferManager;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;

public class EmptyWalletWindow extends Overlay<EmptyWalletWindow> {
    private static final Logger log = LoggerFactory.getLogger(EmptyWalletWindow.class);
    private final WalletService walletService;
    private final WalletPasswordWindow walletPasswordWindow;
    private OpenOfferManager openOfferManager;
    private final BSFormatter formatter;
    private Button emptyWalletButton;
    private InputTextField addressInputTextField;
    private TextField balanceTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public EmptyWalletWindow(WalletService walletService, WalletPasswordWindow walletPasswordWindow,
                             OpenOfferManager openOfferManager, BSFormatter formatter) {
        this.walletService = walletService;
        this.walletPasswordWindow = walletPasswordWindow;
        this.openOfferManager = openOfferManager;
        this.formatter = formatter;

        type = Type.Instruction;
    }

    public void show() {
        if (headLine == null)
            headLine = "Empty wallet";

        width = 700;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        applyStyles();
        display();
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
    
    private void addContent() {
        addMultilineLabel(gridPane, ++rowIndex,
                "Please use that only in emergency case if you cannot access your fund from the UI.\n\n" +
                        "Please note that all open offers will be closed automatically when using this tool.\n\n" +
                        "Before you use this tool, please backup your data directory. " +
                        "You can do this under \"Account/Backup\".\n\n" +
                        "Please file a bug report on Github so that we can investigate what was causing the problem.",
                10);

        Coin totalBalance = walletService.getAvailableBalance();
        balanceTextField = addLabelTextField(gridPane, ++rowIndex, "Your available wallet balance:",
                formatter.formatCoinWithCode(totalBalance), 10).second;

        Tuple2<Label, InputTextField> tuple = addLabelInputTextField(gridPane, ++rowIndex, "Your destination address:");
        addressInputTextField = tuple.second;
        if (BitsquareApp.DEV_MODE)
            addressInputTextField.setText("mjYhQYSbET2bXJDyCdNqYhqSye5QX2WHPz");

        emptyWalletButton = new Button("Empty wallet");
        boolean isBalanceSufficient = Restrictions.isAboveDust(totalBalance);
        emptyWalletButton.setDefaultButton(isBalanceSufficient);
        emptyWalletButton.setDisable(!isBalanceSufficient && addressInputTextField.getText().length() > 0);
        emptyWalletButton.setOnAction(e -> {
            if (addressInputTextField.getText().length() > 0 && isBalanceSufficient) {
                if (walletService.getWallet().isEncrypted()) {
                    walletPasswordWindow
                            .onAesKey(this::doEmptyWallet)
                            .onClose(this::blurAgain)
                            .show();
                } else {
                    doEmptyWallet(null);
                }
            }
        });

        closeButton = new Button("Cancel");
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });
        closeButton.setDefaultButton(!isBalanceSufficient);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(emptyWalletButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }

    private void doEmptyWallet(KeyParameter aesKey) {
        if (!openOfferManager.getOpenOffers().isEmpty()) {
            UserThread.runAfter(() ->
                    new Popup().warning("You have open offers which will be removed if you empty the wallet.\n" +
                            "Are you sure that you want to empty your wallet?")
                            .actionButtonText("Yes, I am sure")
                            .onAction(() -> {
                                doEmptyWallet2(aesKey);
                            })
                            .show(), 300, TimeUnit.MILLISECONDS);
        } else {
            doEmptyWallet2(aesKey);
        }
    }

    private void doEmptyWallet2(KeyParameter aesKey) {
        emptyWalletButton.setDisable(true);
        openOfferManager.removeAllOpenOffers(() -> {
            try {
                walletService.emptyWallet(addressInputTextField.getText(),
                        aesKey,
                        () -> {
                            closeButton.setText("Close");
                            balanceTextField.setText(formatter.formatCoinWithCode(walletService.getAvailableBalance()));
                            emptyWalletButton.setDisable(true);
                            log.debug("wallet empty successful");
                            onClose(() -> UserThread.runAfter(() -> new Popup()
                                    .feedback("The balance of your wallet was successfully transferred.")
                                    .show(), Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS));
                            doClose();
                        },
                        (errorMessage) -> {
                            emptyWalletButton.setDisable(false);
                            log.debug("wallet empty failed " + errorMessage);
                        });
            } catch (InsufficientMoneyException | AddressFormatException e1) {
                e1.printStackTrace();
                log.error(e1.getMessage());
                emptyWalletButton.setDisable(false);
            }
        });
    }
}
