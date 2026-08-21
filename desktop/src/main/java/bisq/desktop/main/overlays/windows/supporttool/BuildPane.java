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

package bisq.desktop.main.overlays.windows.supporttool;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.InputTextField;
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
import bisq.common.util.Tuple2;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

import javafx.geometry.Pos;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.addInputTextField;

@Slf4j
public class BuildPane extends CommonPane {

    private final TradeWalletService tradeWalletService;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final InputsPane inputsPane;
    private final InputTextField buyerSignatureAsHex;
    private final InputTextField sellerSignatureAsHex;
    private final TextArea finalSignedTxHex;

    BuildPane(TradeWalletService tradeWalletService, P2PService p2PService, WalletsSetup walletsSetup, InputsPane inputsPane) {
        this.tradeWalletService = tradeWalletService;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.inputsPane = inputsPane;
        int rowIndexA = 0;
        buyerSignatureAsHex = addInputTextField(this, ++rowIndexA, "buyerSignatureAsHex");
        sellerSignatureAsHex = addInputTextField(this, ++rowIndexA, "sellerSignatureAsHex");
        add(new Label(""), 0, ++rowIndexA);  // spacer
        finalSignedTxHex = new BisqTextArea();
        finalSignedTxHex.setEditable(false);
        finalSignedTxHex.setWrapText(true);
        finalSignedTxHex.setPrefSize(800, 250);
        add(finalSignedTxHex, 0, ++rowIndexA);
        add(new Label(""), 0, ++rowIndexA);  // spacer
        Button buttonBuild = new AutoTooltipButton("Build");
        Button buttonBroadcast = new AutoTooltipButton("Broadcast");
        HBox hBox = new HBox(12, buttonBuild, buttonBroadcast);
        hBox.setAlignment(Pos.BASELINE_CENTER);
        hBox.setPrefWidth(800);
        add(hBox, 0, ++rowIndexA);
        buttonBuild.setOnAction(e -> {
            finalSignedTxHex.setText(buildFinalTx(false));
        });
        buttonBroadcast.setOnAction(e -> {
            finalSignedTxHex.setText(buildFinalTx(true));
        });
    }

    public void activate() {
        finalSignedTxHex.setText("");
        super.activate();
    }

    @Override
    public String getName() {
        return "Build";
    }

    private String buildFinalTx(boolean broadcastIt) {
        String retVal = "";
        inputsPane.calculateTxFee();
        // check that all input fields have been entered, including signatures
        if (!validateInputFieldsAndSignatures()) {
            retVal = "You need to fill in the inputs first";
        } else {
            try {
                // grab data from the inputs pane, build an unsigned tx and write it to the TextArea
                Tuple2<String, String> combined = tradeWalletService.emergencyBuildPayoutTxFrom2of2MultiSig(
                        inputsPane.getDepositTxHex().getText(),
                        InputsPane.getInputFieldAsCoin(inputsPane.getBuyerPayoutAmount()),
                        InputsPane.getInputFieldAsCoin(inputsPane.getSellerPayoutAmount()),
                        InputsPane.getInputFieldAsCoin(inputsPane.getTxFee()),
                        inputsPane.getBuyerAddressString().getText(),
                        inputsPane.getSellerAddressString().getText(),
                        inputsPane.getBuyerPubKeyAsHex().getText(),
                        inputsPane.getSellerPubKeyAsHex().getText(),
                        inputsPane.getDepositTxLegacy().isSelected());
                String redeemScriptHex = combined.first;
                String unsignedTxHex =  combined.second;
                Tuple2<String, String> txIdAndHex = tradeWalletService.emergencyApplySignatureToPayoutTxFrom2of2MultiSig(
                        unsignedTxHex,
                        redeemScriptHex,
                        buyerSignatureAsHex.getText(),
                        sellerSignatureAsHex.getText(),
                        inputsPane.getDepositTxLegacy().isSelected());
                retVal = "txId:{" + txIdAndHex.first + "}\r\ntxHex:{" + txIdAndHex.second + "}";

                if (broadcastIt) {
                    TxBroadcaster.Callback callback = new TxBroadcaster.Callback() {
                        @Override
                        public void onSuccess(@Nullable Transaction result) {
                            log.info("onSuccess");
                            UserThread.execute(() -> {
                                String txId = result != null ? result.getTxId().toString() : "null";
                                new Popup().information("Transaction successfully published. Transaction ID: " + txId).show();
                            });
                        }
                        @Override
                        public void onFailure(TxBroadcastException exception) {
                            log.error(exception.toString());
                            UserThread.execute(() -> new Popup().warning(exception.toString()).show());
                        }
                    };

                    if (GUIUtil.isReadyForTxBroadcastOrShowPopup(p2PService, walletsSetup)) {
                        try {
                            tradeWalletService.emergencyPublishPayoutTxFrom2of2MultiSig(
                                    txIdAndHex.second,
                                    callback);
                        } catch (AddressFormatException | WalletException | TransactionVerificationException ee) {
                            log.error(ee.toString());
                            ee.printStackTrace();
                            UserThread.execute(() -> new Popup().warning(ee.toString()).show());
                        }
                    }
                }
            } catch (IllegalArgumentException | SignatureDecodeException | VerificationException ee) {
                log.error(ee.toString());
                ee.printStackTrace();
                retVal = ee.toString();
            }
        }
        return retVal;
    }

    private boolean validateInputFieldsAndSignatures() {
        return (inputsPane.validateInputFields() &&
                buyerSignatureAsHex.getText().length() > 0 &&
                sellerSignatureAsHex.getText().length() > 0);
    }
}
