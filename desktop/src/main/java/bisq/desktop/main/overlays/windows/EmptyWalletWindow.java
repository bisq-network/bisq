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
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Transitions;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.WalletService;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.Insets;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public class EmptyWalletWindow extends Overlay<EmptyWalletWindow> {
    private static final Logger log = LoggerFactory.getLogger(EmptyWalletWindow.class);
    private final WalletPasswordWindow walletPasswordWindow;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private final OpenOfferManager openOfferManager;

    private Button emptyWalletButton;
    private InputTextField addressInputTextField;
    private TextField balanceTextField;
    private boolean isBtc;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public EmptyWalletWindow(WalletPasswordWindow walletPasswordWindow,
                             OpenOfferManager openOfferManager,
                             P2PService p2PService,
                             WalletsSetup walletsSetup,
                             BtcWalletService btcWalletService,
                             BsqWalletService bsqWalletService,
                             BSFormatter btcFormatter,
                             BsqFormatter bsqFormatter) {
        this.walletPasswordWindow = walletPasswordWindow;
        this.openOfferManager = openOfferManager;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;

        type = Type.Instruction;
    }

    public void show() {
        if (headLine == null)
            headLine = Res.get("emptyWalletWindow.headline", getCurrency());

        width = 768;
        createGridPane();
        addHeadLine();
        addContent();
        applyStyles();
        display();
    }

    private String getCurrency() {
        return isBtc ? "BTC" : "BSQ";
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

    public void setIsBtc(boolean isBtc) {
        this.isBtc = isBtc;
    }

    private void addContent() {

        if (!isBtc)
            gridPane.getColumnConstraints().remove(1);

        if (isBtc)
            addMultilineLabel(gridPane, ++rowIndex, Res.get("emptyWalletWindow.info"), 0);

        Coin totalBalance = getWalletService().getAvailableConfirmedBalance();
        balanceTextField = addTopLabelTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.balance"),
                getFormatter().formatCoinWithCode(totalBalance), 10).second;

        if (isBtc) {
            addressInputTextField = addInputTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.address"));
        } else {
            addTopLabelTextField(gridPane, ++rowIndex, Res.get("emptyWalletWindow.bsq.btcBalance"),
                    bsqFormatter.formatBTCWithCode(bsqWalletService.getAvailableNonBsqBalance().value), 10);
        }
        closeButton = new AutoTooltipButton(Res.get("shared.cancel"));
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(Runnable::run);
        });

        if (isBtc) {
            emptyWalletButton = new AutoTooltipButton(Res.get("emptyWalletWindow.button"));
            boolean isBalanceSufficient = Restrictions.isAboveDust(totalBalance);
            emptyWalletButton.setDefaultButton(isBalanceSufficient);
            emptyWalletButton.setDisable(!isBalanceSufficient && addressInputTextField.getText().length() > 0);
            emptyWalletButton.setOnAction(e -> {
                if (addressInputTextField.getText().length() > 0 && isBalanceSufficient) {
                    if (getWalletService().isEncrypted()) {
                        walletPasswordWindow
                                .onAesKey(this::doEmptyWallet)
                                .onClose(this::blurAgain)
                                .show();
                    } else {
                        doEmptyWallet(null);
                    }
                }
            });

            closeButton.setDefaultButton(!isBalanceSufficient);
        } else {
            closeButton.setDefaultButton(true);
            closeButton.updateText(Res.get("shared.close"));
        }

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);

        if (isBtc)
            hBox.getChildren().addAll(emptyWalletButton, closeButton);
        else
            hBox.getChildren().addAll(closeButton);

        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }

    private void doEmptyWallet(KeyParameter aesKey) {
        if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
            if (!openOfferManager.getObservableList().isEmpty()) {
                UserThread.runAfter(() ->
                        new Popup<>().warning(Res.get("emptyWalletWindow.openOffers.warn"))
                                .actionButtonText(Res.get("emptyWalletWindow.openOffers.yes"))
                                .onAction(() -> doEmptyWallet2(aesKey))
                                .show(), 300, TimeUnit.MILLISECONDS);
            } else {
                doEmptyWallet2(aesKey);
            }
        } else {
            GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
        }
    }

    private void doEmptyWallet2(KeyParameter aesKey) {
        emptyWalletButton.setDisable(true);
        openOfferManager.removeAllOpenOffers(() -> {
            try {
                getWalletService().emptyWallet(addressInputTextField.getText(),
                        aesKey,
                        () -> {
                            closeButton.updateText(Res.get("shared.close"));
                            balanceTextField.setText(getFormatter().formatCoinWithCode(getWalletService().getAvailableConfirmedBalance()));
                            emptyWalletButton.setDisable(true);
                            log.debug("wallet empty successful");
                            onClose(() -> UserThread.runAfter(() -> new Popup<>()
                                    .feedback(Res.get("emptyWalletWindow.sent.success"))
                                    .show(), Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS));
                            doClose();
                        },
                        (errorMessage) -> {
                            emptyWalletButton.setDisable(false);
                            log.error("wallet empty failed {}", errorMessage);
                        });
            } catch (InsufficientMoneyException | AddressFormatException e1) {
                e1.printStackTrace();
                log.error(e1.getMessage());
                emptyWalletButton.setDisable(false);
            }
        });
    }

    private WalletService getWalletService() {
        return isBtc ? btcWalletService : bsqWalletService;
    }

    private BSFormatter getFormatter() {
        return isBtc ? btcFormatter : bsqFormatter;
    }
}
