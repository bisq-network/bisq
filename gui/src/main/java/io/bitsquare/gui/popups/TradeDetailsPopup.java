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

package io.bitsquare.gui.popups;

import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

public class TradeDetailsPopup extends Popup {
    protected static final Logger log = LoggerFactory.getLogger(TradeDetailsPopup.class);

    private final BSFormatter formatter;
    private Trade trade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeDetailsPopup(BSFormatter formatter) {
        this.formatter = formatter;
    }

    public TradeDetailsPopup show(Trade trade) {
        this.trade = trade;

        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        createPopup();
        return this;
    }

    public TradeDetailsPopup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

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

        int rows = 7;
        PaymentAccountContractData buyerPaymentAccountContractData = null;
        PaymentAccountContractData sellerPaymentAccountContractData = null;

        if (offer.getAcceptedCountryCodes() != null)
            rows++;

        if (contract != null) {
            buyerPaymentAccountContractData = contract.getBuyerPaymentAccountContractData();
            sellerPaymentAccountContractData = contract.getSellerPaymentAccountContractData();
            if (buyerPaymentAccountContractData != null)
                rows++;

            if (sellerPaymentAccountContractData != null)
                rows++;

            if (buyerPaymentAccountContractData == null && sellerPaymentAccountContractData == null)
                rows++;

            if (contract.takeOfferFeeTxID != null)
                rows++;
        }

        if (trade.getDepositTx() != null)
            rows++;
        if (trade.getPayoutTx() != null)
            rows++;
        if (trade.errorMessageProperty().get() != null)
            rows += 2;

        addTitledGroupBg(gridPane, ++rowIndex, rows, "Trade details");
        addLabelTextField(gridPane, rowIndex, "Trade ID:", trade.getId(), Layout.FIRST_ROW_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, "Trade date:", formatter.formatDateTime(trade.getDate()));
        String direction = offer.getDirection() == Offer.Direction.BUY ? "Offerer as buyer / Taker as seller" : "Offerer as seller / Taker as buyer";
        addLabelTextField(gridPane, ++rowIndex, "Offer direction:", direction);
        addLabelTextField(gridPane, ++rowIndex, "Price:", formatter.formatFiat(offer.getPrice()) + " " + offer.getCurrencyCode());
        addLabelTextField(gridPane, ++rowIndex, "Trade amount:", formatter.formatCoinWithCode(trade.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex, "Selected arbitrator:", formatter.arbitratorAddressToShortAddress(trade.getArbitratorAddress()));

        if (contract != null) {
            if (buyerPaymentAccountContractData != null)
                addLabelTextField(gridPane, ++rowIndex, "Buyer payment details:", BSResources.get(buyerPaymentAccountContractData.getPaymentDetails()));

            if (sellerPaymentAccountContractData != null)
                addLabelTextField(gridPane, ++rowIndex, "Seller payment details:", BSResources.get(sellerPaymentAccountContractData.getPaymentDetails()));

            if (buyerPaymentAccountContractData == null && sellerPaymentAccountContractData == null)
                addLabelTextField(gridPane, ++rowIndex, "Payment method:", BSResources.get(contract.getPaymentMethodName()));
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, "Create offer fee transaction ID:", offer.getOfferFeePaymentTxID());
        if (contract != null && contract.takeOfferFeeTxID != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Take offer fee transaction ID:", contract.takeOfferFeeTxID);

        if (trade.getDepositTx() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Deposit transaction ID:", trade.getDepositTx().getHashAsString());
        if (trade.getPayoutTx() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Payout transaction ID:", trade.getPayoutTx().getHashAsString());

        if (trade.errorMessageProperty().get() != null) {
            TextArea textArea = addLabelTextArea(gridPane, ++rowIndex, "Error message:", "").second;
            textArea.setText(trade.errorMessageProperty().get());
            textArea.setEditable(false);

            IntegerProperty count = new SimpleIntegerProperty(20);
            int rowHeight = 10;
            textArea.prefHeightProperty().bindBidirectional(count);
            textArea.scrollTopProperty().addListener((ov, old, newVal) -> {
                if (newVal.intValue() > rowHeight)
                    count.setValue(count.get() + newVal.intValue() + 10);
            });
            textArea.setScrollTop(30);

            TextField state = addLabelTextField(gridPane, ++rowIndex, "Trade state:").second;
            state.setText(trade.getState().getPhase().name());
            //TODO better msg display
     /*       switch (trade.getTradeState().getPhase()) {
                case PREPARATION:
                    state.setText("Take offer fee is already paid.");
                    break;
                case TAKER_FEE_PAID:
                    state.setText("Take offer fee is already paid.");
                    break;
                case DEPOSIT_PAID:
                case FIAT_SENT:
                case FIAT_RECEIVED:
                    state.setText("Deposit is already paid.");
                    break;
                case PAYOUT_PAID:
                    break;
                case WITHDRAWN:
                    break;
                case DISPUTE:
                    break;
            }*/
        }

        Button cancelButton = addButtonAfterGroup(gridPane, ++rowIndex, "Close");
        cancelButton.requestFocus();
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
            hide();
        });
    }
}
