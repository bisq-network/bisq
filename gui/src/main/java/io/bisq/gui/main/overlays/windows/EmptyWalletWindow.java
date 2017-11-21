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

package io.bisq.gui.main.overlays.windows;

import io.bisq.common.UserThread;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.WalletService;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Transitions;
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

import static io.bisq.gui.util.FormBuilder.*;

public class EmptyWalletWindow extends Overlay<EmptyWalletWindow> {
    private static final Logger log = LoggerFactory.getLogger(EmptyWalletWindow.class);
    private final WalletPasswordWindow walletPasswordWindow;
    private final BSFormatter formatter;
    private final OpenOfferManager openOfferManager;

    private Button emptyWalletButton;
    private InputTextField addressInputTextField;
    private TextField balanceTextField;
    private WalletService walletService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public EmptyWalletWindow(WalletPasswordWindow walletPasswordWindow,
                             OpenOfferManager openOfferManager, BSFormatter formatter) {
        this.walletPasswordWindow = walletPasswordWindow;
        this.openOfferManager = openOfferManager;
        this.formatter = formatter;

        type = Type.Instruction;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("emptyWalletWindow.headline");

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
        addMultilineLabel(gridPane, ++rowIndex, Res.get("emptyWalletWindow.info"), 10);

        Coin totalBalance = walletService.getAvailableBalance();
        balanceTextField = addLabelTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.balance"),
                formatter.formatCoinWithCode(totalBalance), 10).second;

        Tuple2<Label, InputTextField> tuple = addLabelInputTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.address"));
        addressInputTextField = tuple.second;
        emptyWalletButton = new Button(Res.get("emptyWalletWindow.button"));
        boolean isBalanceSufficient = Restrictions.isAboveDust(totalBalance);
        emptyWalletButton.setDefaultButton(isBalanceSufficient);
        emptyWalletButton.setDisable(!isBalanceSufficient && addressInputTextField.getText().length() > 0);
        emptyWalletButton.setOnAction(e -> {
            if (addressInputTextField.getText().length() > 0 && isBalanceSufficient) {
                if (walletService.isEncrypted()) {
                    walletPasswordWindow
                            .onAesKey(this::doEmptyWallet)
                            .onClose(this::blurAgain)
                            .show();
                } else {
                    doEmptyWallet(null);
                }
            }
        });

        closeButton = new Button(Res.get("shared.cancel"));
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
        if (!openOfferManager.getObservableList().isEmpty()) {
            UserThread.runAfter(() ->
                    new Popup<>().warning(Res.get("emptyWalletWindow.openOffers.warn"))
                            .actionButtonText(Res.get("emptyWalletWindow.openOffers.yes"))
                            .onAction(() -> doEmptyWallet2(aesKey))
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
                            closeButton.setText(Res.get("shared.close"));
                            balanceTextField.setText(formatter.formatCoinWithCode(walletService.getAvailableBalance()));
                            emptyWalletButton.setDisable(true);
                            log.debug("wallet empty successful");
                            onClose(() -> UserThread.runAfter(() -> new Popup<>()
                                    .feedback(Res.get("emptyWalletWindow.sent.success"))
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

    public void setwalletService(WalletService walletService) {
        this.walletService = walletService;
    }
}
