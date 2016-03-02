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

import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Transitions;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.reactfx.util.FxTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import java.time.Duration;

import static io.bitsquare.gui.util.FormBuilder.*;

public class EmptyWalletWindow extends Overlay<EmptyWalletWindow> {
    private static final Logger log = LoggerFactory.getLogger(EmptyWalletWindow.class);
    private final WalletService walletService;
    private final WalletPasswordWindow walletPasswordWindow;
    private final BSFormatter formatter;
    private Button emptyWalletButton;
    private InputTextField addressInputTextField;
    private TextField addressTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public EmptyWalletWindow(WalletService walletService, WalletPasswordWindow walletPasswordWindow, BSFormatter formatter) {
        this.walletService = walletService;
        this.walletPasswordWindow = walletPasswordWindow;
        this.formatter = formatter;
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

    private void addContent() {
        addMultilineLabel(gridPane, ++rowIndex,
                "Please use that only in emergency case if you cannot access your fund from the UI.\n" +
                        "Before you use this tool, you should backup your data directory. After you have successfully transferred your wallet balance, remove" +
                        " the db directory inside the data directory to start with a newly created and consistent data structure.\n" +
                        "Please make a bug report on Github so that we can investigate what was causing the problem.",
                10);

        Coin totalBalance = walletService.getAvailableBalance();
        addressTextField = addLabelTextField(gridPane, ++rowIndex, "Your available wallet balance:",
                formatter.formatCoinWithCode(totalBalance), 10).second;
        Tuple2<Label, InputTextField> tuple = addLabelInputTextField(gridPane, ++rowIndex, "Your destination address:");
        addressInputTextField = tuple.second;
        emptyWalletButton = new Button("Empty wallet");
        boolean isBalanceSufficient = Restrictions.isAboveDust(totalBalance);
        emptyWalletButton.setDefaultButton(isBalanceSufficient);
        emptyWalletButton.setDisable(!isBalanceSufficient && addressInputTextField.getText().length() > 0);
        emptyWalletButton.setOnAction(e -> {
            if (addressInputTextField.getText().length() > 0 && isBalanceSufficient) {
                if (walletService.getWallet().isEncrypted()) {
                    walletPasswordWindow
                            .onClose(() -> blurAgain())
                            .onAesKey(aesKey -> doEmptyWallet(aesKey))
                            .show();
                } else {
                    doEmptyWallet(null);
                }
            }
        });

        closeButton = new Button("Cancel");
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
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
        emptyWalletButton.setDisable(true);
        try {
            walletService.emptyWallet(addressInputTextField.getText(),
                    aesKey,
                    () -> {
                        closeButton.setText("Close");
                        addressTextField.setText(formatter.formatCoinWithCode(walletService.getAvailableBalance()));
                        emptyWalletButton.setDisable(true);
                        log.debug("wallet empty successful");
                        FxTimer.runLater(Duration.ofMillis(Transitions.DEFAULT_DURATION), () -> new Popup()
                                .feedback("The balance of your wallet was successfully transferred.")
                                .onClose(() -> blurAgain()).show());
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
    }

}
