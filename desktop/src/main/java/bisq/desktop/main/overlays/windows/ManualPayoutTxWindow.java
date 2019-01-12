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

import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addInputTextField;

// We dont translate here as it is for dev only purpose
public class ManualPayoutTxWindow extends Overlay<ManualPayoutTxWindow> {
    private static final Logger log = LoggerFactory.getLogger(ManualPayoutTxWindow.class);
    private final TradeWalletService tradeWalletService;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ManualPayoutTxWindow(TradeWalletService tradeWalletService, P2PService p2PService, WalletsSetup walletsSetup) {
        this.tradeWalletService = tradeWalletService;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        type = Type.Attention;
    }

    public void show() {
        if (headLine == null)
            headLine = "Emergency MultiSig payout tool"; // We dont translate here as it is for dev only purpose

        width = 1068;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
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
        gridPane.getColumnConstraints().remove(1);
        // We dont translate here as it is for dev only purpose
        InputTextField depositTxHex = addInputTextField(gridPane, ++rowIndex, "depositTxHex");

        InputTextField buyerPayoutAmount = addInputTextField(gridPane, ++rowIndex, "buyerPayoutAmount");
        InputTextField sellerPayoutAmount = addInputTextField(gridPane, ++rowIndex, "sellerPayoutAmount");
        InputTextField arbitratorPayoutAmount = addInputTextField(gridPane, ++rowIndex, "arbitratorPayoutAmount");
        InputTextField txFee = addInputTextField(gridPane, ++rowIndex, "Tx fee");

        InputTextField buyerAddressString = addInputTextField(gridPane, ++rowIndex, "buyerAddressString");
        InputTextField sellerAddressString = addInputTextField(gridPane, ++rowIndex, "sellerAddressString");
        InputTextField arbitratorAddressString = addInputTextField(gridPane, ++rowIndex, "arbitratorAddressString");

        InputTextField buyerPrivateKeyAsHex = addInputTextField(gridPane, ++rowIndex, "buyerPrivateKeyAsHex");
        InputTextField sellerPrivateKeyAsHex = addInputTextField(gridPane, ++rowIndex, "sellerPrivateKeyAsHex");
        InputTextField arbitratorPrivateKeyAsHex = addInputTextField(gridPane, ++rowIndex, "arbitratorPrivateKeyAsHex");

        InputTextField buyerPubKeyAsHex = addInputTextField(gridPane, ++rowIndex, "buyerPubKeyAsHex");
        InputTextField sellerPubKeyAsHex = addInputTextField(gridPane, ++rowIndex, "sellerPubKeyAsHex");
        InputTextField arbitratorPubKeyAsHex = addInputTextField(gridPane, ++rowIndex, "arbitratorPubKeyAsHex");

        InputTextField P2SHMultiSigOutputScript = addInputTextField(gridPane, ++rowIndex, "P2SHMultiSigOutputScript");


        // Notes:
        // Open with alt+g and enable DEV mode
        // Priv key is only visible if pw protection is removed (wallet details data (alt+j))
        // Take P2SHMultiSigOutputScript from depositTx in blockexplorer
        // Take missing buyerPubKeyAsHex and sellerPubKeyAsHex from contract data!
        // Lookup sellerPrivateKeyAsHex associated with sellerPubKeyAsHex (or buyers) in wallet details data
        // sellerPubKeys/buyerPubKeys are auto generated if used the fields below
        // Never set the priv arbitr. key here!

        depositTxHex.setText("");
        P2SHMultiSigOutputScript.setText("");

        buyerPayoutAmount.setText("");
        sellerPayoutAmount.setText("");
        arbitratorPayoutAmount.setText("0");

        buyerAddressString.setText("");
        buyerPubKeyAsHex.setText("");
        buyerPrivateKeyAsHex.setText("");

        sellerAddressString.setText("");
        sellerPubKeyAsHex.setText("");
        sellerPrivateKeyAsHex.setText("");

        arbitratorAddressString.setText("");
        arbitratorPubKeyAsHex.setText("");

        actionButtonText("Sign and publish transaction");

        TxBroadcaster.Callback callback = new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                log.error("onSuccess");
                UserThread.execute(() -> {
                    String txId = result != null ? result.getHashAsString() : "null";
                    new Popup<>()
                            .information("Transaction successful published. Transaction ID: " + txId)
                            .show();
                });
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                log.error(exception.toString());
                UserThread.execute(() -> new Popup<>().warning(exception.toString()).show());
            }
        };
        onAction(() -> {
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                try {
                    tradeWalletService.emergencySignAndPublishPayoutTx(depositTxHex.getText(),
                            Coin.parseCoin(buyerPayoutAmount.getText()),
                            Coin.parseCoin(sellerPayoutAmount.getText()),
                            Coin.parseCoin(arbitratorPayoutAmount.getText()),
                            Coin.parseCoin(txFee.getText()),
                            buyerAddressString.getText(),
                            sellerAddressString.getText(),
                            arbitratorAddressString.getText(),
                            buyerPrivateKeyAsHex.getText(),
                            sellerPrivateKeyAsHex.getText(),
                            arbitratorPrivateKeyAsHex.getText(),
                            buyerPubKeyAsHex.getText(),
                            sellerPubKeyAsHex.getText(),
                            arbitratorPubKeyAsHex.getText(),
                            P2SHMultiSigOutputScript.getText(),
                            callback);
                } catch (AddressFormatException | WalletException | TransactionVerificationException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    UserThread.execute(() -> new Popup<>().warning(e.toString()).show());
                }
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    @Override
    protected void addButtons() {
        super.addButtons();
        actionButton.setOnAction(event -> actionHandlerOptional.ifPresent(Runnable::run));
    }
}
