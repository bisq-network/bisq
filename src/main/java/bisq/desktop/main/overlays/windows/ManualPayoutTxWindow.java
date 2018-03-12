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
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.WalletsSetup;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addLabelInputTextField;

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

        width = 1000;
        createGridPane();
        addHeadLine();
        addSeparator();
        addContent();
        addCloseButton();
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
        // We dont translate here as it is for dev only purpose
        InputTextField depositTxHex = addLabelInputTextField(gridPane, ++rowIndex, "depositTxHex:").second;

        InputTextField buyerPayoutAmount = addLabelInputTextField(gridPane, ++rowIndex, "buyerPayoutAmount:").second;
        InputTextField sellerPayoutAmount = addLabelInputTextField(gridPane, ++rowIndex, "sellerPayoutAmount:").second;
        InputTextField arbitratorPayoutAmount = addLabelInputTextField(gridPane, ++rowIndex, "arbitratorPayoutAmount:").second;
        InputTextField txFee = addLabelInputTextField(gridPane, ++rowIndex, "Tx fee:").second;

        InputTextField buyerAddressString = addLabelInputTextField(gridPane, ++rowIndex, "buyerAddressString:").second;
        InputTextField sellerAddressString = addLabelInputTextField(gridPane, ++rowIndex, "sellerAddressString:").second;
        InputTextField arbitratorAddressString = addLabelInputTextField(gridPane, ++rowIndex, "arbitratorAddressString:").second;

        InputTextField buyerPrivateKeyAsHex = addLabelInputTextField(gridPane, ++rowIndex, "buyerPrivateKeyAsHex:").second;
        InputTextField sellerPrivateKeyAsHex = addLabelInputTextField(gridPane, ++rowIndex, "sellerPrivateKeyAsHex:").second;
        InputTextField arbitratorPrivateKeyAsHex = addLabelInputTextField(gridPane, ++rowIndex, "arbitratorPrivateKeyAsHex:").second;

        InputTextField buyerPubKeyAsHex = addLabelInputTextField(gridPane, ++rowIndex, "buyerPubKeyAsHex:").second;
        InputTextField sellerPubKeyAsHex = addLabelInputTextField(gridPane, ++rowIndex, "sellerPubKeyAsHex:").second;
        InputTextField arbitratorPubKeyAsHex = addLabelInputTextField(gridPane, ++rowIndex, "arbitratorPubKeyAsHex:").second;

        InputTextField P2SHMultiSigOutputScript = addLabelInputTextField(gridPane, ++rowIndex, "P2SHMultiSigOutputScript:").second;


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

        FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
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
            public void onFailure(@NotNull Throwable t) {
                log.error(t.toString());
                log.error("onFailure");
                UserThread.execute(() -> new Popup<>().warning(t.toString()).show());
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
    protected void addCloseButton() {
        super.addCloseButton();
        actionButton.setOnAction(event -> actionHandlerOptional.ifPresent(Runnable::run));
    }
}
