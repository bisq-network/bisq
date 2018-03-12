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

import bisq.desktop.components.TextFieldWithCopyIcon;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.Layout;

import bisq.core.arbitration.DisputeManager;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;

import bisq.common.locale.Res;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import javafx.geometry.Insets;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.*;

public class TradeDetailsWindow extends Overlay<TradeDetailsWindow> {
    protected static final Logger log = LoggerFactory.getLogger(TradeDetailsWindow.class);

    private final BSFormatter formatter;
    private final DisputeManager disputeManager;
    private final TradeManager tradeManager;
    private Trade trade;
    private ChangeListener<Number> changeListener;
    private TextArea textArea;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeDetailsWindow(BSFormatter formatter, DisputeManager disputeManager, TradeManager tradeManager) {
        this.formatter = formatter;
        this.disputeManager = disputeManager;
        this.tradeManager = tradeManager;
        type = Type.Confirmation;
    }

    public void show(Trade trade) {
        this.trade = trade;

        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void cleanup() {
        if (textArea != null)
            textArea.scrollTopProperty().addListener(changeListener);
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        Offer offer = trade.getOffer();
        Contract contract = trade.getContract();

        int rows = 5;
        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("tradeDetailsWindow.headline"));

        boolean myOffer = tradeManager.isMyOffer(offer);
        String fiatDirectionInfo;
        String btcDirectionInfo;
        String toReceive = " " + Res.get("shared.toReceive");
        String toSpend = " " + Res.get("shared.toSpend");
        String offerType = Res.getWithCol("shared.offerType");
        if (tradeManager.isBuyer(offer)) {
            addLabelTextField(gridPane, rowIndex, offerType,
                    formatter.getDirectionForBuyer(myOffer, offer.getCurrencyCode()), Layout.FIRST_ROW_DISTANCE);
            fiatDirectionInfo = toSpend;
            btcDirectionInfo = toReceive;
        } else {
            addLabelTextField(gridPane, rowIndex, offerType,
                    formatter.getDirectionForSeller(myOffer, offer.getCurrencyCode()), Layout.FIRST_ROW_DISTANCE);
            fiatDirectionInfo = toReceive;
            btcDirectionInfo = toSpend;
        }

        addLabelTextField(gridPane, ++rowIndex, Res.get("shared.btcAmount") + btcDirectionInfo,
                formatter.formatCoinWithCode(trade.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex,
                formatter.formatVolumeLabel(offer.getCurrencyCode()) + fiatDirectionInfo,
                formatter.formatVolumeWithCode(trade.getTradeVolume()));
        addLabelTextField(gridPane, ++rowIndex, Res.get("shared.tradePrice"),
                formatter.formatPrice(trade.getTradePrice()));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.paymentMethod"),
                Res.get(offer.getPaymentMethod().getId()));

        // second group
        rows = 6;
        PaymentAccountPayload buyerPaymentAccountPayload = null;
        PaymentAccountPayload sellerPaymentAccountPayload = null;

       /* if (offer.getAcceptedCountryCodes() != null)
            rows++;
        if (offer.getAcceptedBanks() != null)
            rows++;*/

        if (contract != null) {
            rows++;

            buyerPaymentAccountPayload = contract.getBuyerPaymentAccountPayload();
            sellerPaymentAccountPayload = contract.getSellerPaymentAccountPayload();
            if (buyerPaymentAccountPayload != null)
                rows++;

            if (sellerPaymentAccountPayload != null)
                rows++;

            if (buyerPaymentAccountPayload == null && sellerPaymentAccountPayload == null)
                rows++;
        }

        if (trade.getTakerFeeTxId() != null)
            rows++;
        if (trade.getDepositTx() != null)
            rows++;
        if (trade.getPayoutTx() != null)
            rows++;
        boolean showDisputedTx = disputeManager.findOwnDispute(trade.getId()).isPresent() &&
                disputeManager.findOwnDispute(trade.getId()).get().getDisputePayoutTxId() != null;
        if (showDisputedTx)
            rows++;
        if (trade.hasFailed())
            rows += 2;
        if (trade.getTradingPeerNodeAddress() != null)
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("shared.details"), Layout.GROUP_DISTANCE);
        addLabelTextFieldWithCopyIcon(gridPane, rowIndex, Res.get("shared.tradeId"),
                trade.getId(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradeDate"),
                formatter.formatDateTime(trade.getDate()));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(offer.getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(offer.getSellerSecurityDeposit());
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.securityDeposit"), securityDeposit);

        String txFee = Res.get("shared.makerTxFee", formatter.formatCoinWithCode(offer.getTxFee())) +
                " / " +
                Res.get("shared.takerTxFee", formatter.formatCoinWithCode(offer.getTxFee().multiply(3L)));
        addLabelTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.txFee"), txFee);

        if (trade.getArbitratorNodeAddress() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("shared.arbitrator"),
                    trade.getArbitratorNodeAddress().getFullAddress());

        if (trade.getTradingPeerNodeAddress() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradingPeersOnion"),
                    trade.getTradingPeerNodeAddress().getFullAddress());

        if (contract != null) {
            if (buyerPaymentAccountPayload != null) {
                TextFieldWithCopyIcon tf = addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex,
                        Res.get("shared.paymentDetails", Res.get("shared.buyer")),
                        buyerPaymentAccountPayload.getPaymentDetails()).second;
                tf.setTooltip(new Tooltip(tf.getText()));
            }
            if (sellerPaymentAccountPayload != null) {
                TextFieldWithCopyIcon tf = addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex,
                        Res.get("shared.paymentDetails", Res.get("shared.seller")),
                        sellerPaymentAccountPayload.getPaymentDetails()).second;
                tf.setTooltip(new Tooltip(tf.getText()));
            }
            if (buyerPaymentAccountPayload == null && sellerPaymentAccountPayload == null)
                addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.paymentMethod"),
                        Res.get(contract.getPaymentMethodId()));
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.makerFeeTxId"), offer.getOfferFeePaymentTxId());
        if (trade.getTakerFeeTxId() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.takerFeeTxId"), trade.getTakerFeeTxId());

        if (trade.getDepositTx() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.getWithCol("shared.depositTransactionId"),
                    trade.getDepositTx().getHashAsString());
        if (trade.getPayoutTx() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.payoutTxId"),
                    trade.getPayoutTx().getHashAsString());
        if (showDisputedTx)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.disputedPayoutTxId"),
                    disputeManager.findOwnDispute(trade.getId()).get().getDisputePayoutTxId());

        if (contract != null) {
            Button viewContractButton = addLabelButton(gridPane, ++rowIndex, Res.get("shared.contractAsJson"),
                    Res.get("shared.viewContractAsJson"), 0).second;
            viewContractButton.setDefaultButton(false);
            viewContractButton.setOnAction(e -> {
                TextArea textArea = new TextArea();
                textArea.setText(trade.getContractAsJson());
                String contractAsJson = trade.getContractAsJson();
                contractAsJson += "\n\nBuyerMultiSigPubKeyHex: " + Utils.HEX.encode(contract.getBuyerMultiSigPubKey());
                contractAsJson += "\nSellerMultiSigPubKeyHex: " + Utils.HEX.encode(contract.getSellerMultiSigPubKey());
                textArea.setText(contractAsJson);
                textArea.setPrefHeight(50);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setPrefSize(800, 600);

                Scene viewContractScene = new Scene(textArea);
                Stage viewContractStage = new Stage();
                viewContractStage.setTitle(Res.get("shared.contract.title", trade.getShortId()));
                viewContractStage.setScene(viewContractScene);
                if (owner == null)
                    owner = MainView.getRootContainer();
                Scene rootScene = owner.getScene();
                viewContractStage.initOwner(rootScene.getWindow());
                viewContractStage.initModality(Modality.NONE);
                viewContractStage.initStyle(StageStyle.UTILITY);
                viewContractStage.show();

                Window window = rootScene.getWindow();
                double titleBarHeight = window.getHeight() - rootScene.getHeight();
                viewContractStage.setX(Math.round(window.getX() + (owner.getWidth() - viewContractStage.getWidth()) / 2) + 200);
                viewContractStage.setY(Math.round(window.getY() + titleBarHeight + (owner.getHeight() - viewContractStage.getHeight()) / 2) + 50);
            });
        }

        if (trade.hasFailed()) {
            textArea = addLabelTextArea(gridPane, ++rowIndex, Res.get("shared.errorMessage"), "").second;
            textArea.setText(trade.getErrorMessage());
            textArea.setEditable(false);

            IntegerProperty count = new SimpleIntegerProperty(20);
            int rowHeight = 10;
            textArea.prefHeightProperty().bindBidirectional(count);
            changeListener = (ov, old, newVal) -> {
                if (newVal.intValue() > rowHeight)
                    count.setValue(count.get() + newVal.intValue() + 10);
            };
            textArea.scrollTopProperty().addListener(changeListener);
            textArea.setScrollTop(30);

            TextField state = addLabelTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradeState")).second;
            state.setText(trade.getState().getPhase().name());
        }

        Button closeButton = addButtonAfterGroup(gridPane, ++rowIndex, Res.get("shared.close"));
        //TODO app wide focus
        //closeButton.requestFocus();
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
