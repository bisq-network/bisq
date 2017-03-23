/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.overlays.windows;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.UserThread;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.main.overlays.popups.Popup;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.bisq.gui.util.FormBuilder.addLabelInputTextField;

// We dont translate here as it is for dev only purpose
public class SpendFromDepositTxWindow extends Overlay<SpendFromDepositTxWindow> {
    private static final Logger log = LoggerFactory.getLogger(SpendFromDepositTxWindow.class);
    private final TradeWalletService tradeWalletService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SpendFromDepositTxWindow(TradeWalletService tradeWalletService) {
        this.tradeWalletService = tradeWalletService;
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

        buyerPayoutAmount.setText("1.03");
        sellerPayoutAmount.setText("0.03");
        arbitratorPayoutAmount.setText("0");

        buyerAddressString.setText("");
        buyerPubKeyAsHex.setText("");
        buyerPrivateKeyAsHex.setText("");

        sellerAddressString.setText("");
        sellerPubKeyAsHex.setText("");
        sellerPrivateKeyAsHex.setText("");

        //4.9
        // arbitratorAddressString.setText("19xdeiQM2Hn2M2wbpT5imcYWzqhiSDHPy4");
        // arbitratorPubKeyAsHex.setText("02c62e794fe67f3a2115e2de4757143ff7f27bdf38aa4ae58a3595baa6d676875b");

        // 4.2
        arbitratorAddressString.setText("1FdFzBazmHQxbUbdCUJwuCtR37DrZrEobu");
        arbitratorPubKeyAsHex.setText("030fdc2ebc297df4047442f6079f1ce3b7d1938a41f88bd11497545cc94fcfd315");

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
        });
    }

    @Override
    protected void addCloseButton() {
        super.addCloseButton();
        actionButton.setOnAction(event -> actionHandlerOptional.ifPresent(Runnable::run));
    }
}
