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

import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.gui.components.TextFieldWithCopyIcon;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;

public class TradeDetailsWindow extends Overlay<TradeDetailsWindow> {
    protected static final Logger log = LoggerFactory.getLogger(TradeDetailsWindow.class);

    private final BSFormatter formatter;
    private final DisputeManager disputeManager;
    private TradeManager tradeManager;
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
        gridPane.setStyle("-fx-background-color: -bs-content-bg-grey;" +
                        "-fx-background-radius: 5 5 5 5;" +
                        "-fx-effect: dropshadow(gaussian, #999, 10, 0, 0, 0);" +
                        "-fx-background-insets: 10;"
        );
    }

    private void addContent() {
        Offer offer = trade.getOffer();
        Contract contract = trade.getContract();

        int rows = 5;
        addTitledGroupBg(gridPane, ++rowIndex, rows, "Trade");

        boolean myOffer = tradeManager.isMyOffer(offer);
        String fiatDirectionInfo;
        String btcDirectionInfo;
        if (tradeManager.isBuyer(offer)) {
            addLabelTextField(gridPane, rowIndex, "Trade type:", formatter.getDirectionForBuyer(myOffer, offer.getCurrencyCode()), Layout.FIRST_ROW_DISTANCE);
            fiatDirectionInfo = " to spend:";
            btcDirectionInfo = " to receive:";
        } else {
            addLabelTextField(gridPane, rowIndex, "Trade type:", formatter.getDirectionForSeller(myOffer, offer.getCurrencyCode()), Layout.FIRST_ROW_DISTANCE);
            fiatDirectionInfo = " to receive:";
            btcDirectionInfo = " to spend:";
        }

        addLabelTextField(gridPane, ++rowIndex, "Bitcoin amount" + btcDirectionInfo, formatter.formatCoinWithCode(trade.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex, formatter.formatVolumeLabel(offer.getCurrencyCode()) + fiatDirectionInfo, formatter.formatVolumeWithCode(trade.getTradeVolume()));
        addLabelTextField(gridPane, ++rowIndex, "Trade price:", formatter.formatPrice(trade.getTradePrice()));
        addLabelTextField(gridPane, ++rowIndex, "Payment method:", BSResources.get(offer.getPaymentMethod().getId()));

        // second group
        rows = 5;
        PaymentAccountContractData buyerPaymentAccountContractData = null;
        PaymentAccountContractData sellerPaymentAccountContractData = null;

       /* if (offer.getAcceptedCountryCodes() != null)
            rows++;
        if (offer.getAcceptedBanks() != null)
            rows++;*/

        if (contract != null) {
            rows++;

            buyerPaymentAccountContractData = contract.getBuyerPaymentAccountContractData();
            sellerPaymentAccountContractData = contract.getSellerPaymentAccountContractData();
            if (buyerPaymentAccountContractData != null)
                rows++;

            if (sellerPaymentAccountContractData != null)
                rows++;

            if (buyerPaymentAccountContractData == null && sellerPaymentAccountContractData == null)
                rows++;
        }

        if (trade.getTakeOfferFeeTxId() != null)
            rows++;
        if (trade.getDepositTx() != null)
            rows++;
        if (trade.getPayoutTx() != null)
            rows++;
        boolean showDisputedTx = disputeManager.findOwnDispute(trade.getId()).isPresent() && disputeManager.findOwnDispute(trade.getId()).get().getDisputePayoutTxId() != null;
        if (showDisputedTx)
            rows++;
        if (trade.errorMessageProperty().get() != null)
            rows += 2;
        if (trade.getTradingPeerNodeAddress() != null)
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, "Details", Layout.GROUP_DISTANCE);
        addLabelTextFieldWithCopyIcon(gridPane, rowIndex, "Trade ID:", trade.getId(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, "Trade date:", formatter.formatDateTime(trade.getDate()));
        addLabelTextField(gridPane, ++rowIndex, "Security deposit:", formatter.formatCoinWithCode(FeePolicy.getSecurityDeposit()));
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Selected arbitrator:", trade.getArbitratorNodeAddress().getFullAddress());

        if (trade.getTradingPeerNodeAddress() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Trading peers onion address:", trade.getTradingPeerNodeAddress().getFullAddress());

        if (contract != null) {
            if (buyerPaymentAccountContractData != null) {
                TextFieldWithCopyIcon tf = addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC buyer payment details:", BSResources.get(buyerPaymentAccountContractData.getPaymentDetails())).second;
                tf.setTooltip(new Tooltip(tf.getText()));
            }
            if (sellerPaymentAccountContractData != null) {
                TextFieldWithCopyIcon tf = addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC seller payment details:", BSResources.get(sellerPaymentAccountContractData.getPaymentDetails())).second;
                tf.setTooltip(new Tooltip(tf.getText()));
            }
            if (buyerPaymentAccountContractData == null && sellerPaymentAccountContractData == null)
                addLabelTextField(gridPane, ++rowIndex, "Payment method:", BSResources.get(contract.getPaymentMethodName()));
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, "Offer fee transaction ID:", offer.getOfferFeePaymentTxID());
        if (trade.getTakeOfferFeeTxId() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Taker fee transaction ID:", trade.getTakeOfferFeeTxId());

        if (trade.getDepositTx() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Deposit transaction ID:", trade.getDepositTx().getHashAsString());
        if (trade.getPayoutTx() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Payout transaction ID:", trade.getPayoutTx().getHashAsString());
        if (showDisputedTx)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Disputed payout transaction ID:", disputeManager.findOwnDispute(trade.getId()).get().getDisputePayoutTxId());

        if (contract != null) {
            Button viewContractButton = addLabelButton(gridPane, ++rowIndex, "Contract in JSON format:", "View contract", 0).second;
            viewContractButton.setDefaultButton(false);
            viewContractButton.setOnAction(e -> {
                TextArea textArea = new TextArea();
                textArea.setText(trade.getContractAsJson());
                textArea.setPrefHeight(50);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setPrefSize(800, 600);

                Scene viewContractScene = new Scene(textArea);
                Stage viewContractStage = new Stage();
                viewContractStage.setTitle("Contract for trade with ID: " + trade.getShortId());
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

        if (trade.errorMessageProperty().get() != null) {
            textArea = addLabelTextArea(gridPane, ++rowIndex, "Error message:", "").second;
            textArea.setText(trade.errorMessageProperty().get());
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

            TextField state = addLabelTextField(gridPane, ++rowIndex, "Trade state:").second;
            state.setText(trade.getState().getPhase().name());
        }

        Button cancelButton = addButtonAfterGroup(gridPane, ++rowIndex, "Close");
        //TODO app wide focus
        //cancelButton.requestFocus();
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
            hide();
        });
    }
}
