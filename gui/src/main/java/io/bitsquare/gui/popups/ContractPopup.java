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

import io.bitsquare.arbitration.Dispute;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.payment.BlockChainAccountContractData;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.offer.Offer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

public class ContractPopup extends Popup {
    protected static final Logger log = LoggerFactory.getLogger(ContractPopup.class);

    private final BSFormatter formatter;
    private Dispute dispute;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ContractPopup(BSFormatter formatter) {
        this.formatter = formatter;
    }

    public ContractPopup show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        display();
        return this;
    }

    public ContractPopup onClose(Runnable closeHandler) {
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
        Contract contract = dispute.getContract();
        Offer offer = contract.offer;

        int rows = 16;
        if (dispute.getDepositTxSerialized() != null)
            rows++;
        if (dispute.getPayoutTxSerialized() != null)
            rows++;
        if (offer.getAcceptedCountryCodes() != null)
            rows++;

        boolean isPaymentIdAvailable = false;
        PaymentAccountContractData sellerPaymentAccountContractData = contract.getSellerPaymentAccountContractData();
        if (sellerPaymentAccountContractData instanceof BlockChainAccountContractData &&
                ((BlockChainAccountContractData) sellerPaymentAccountContractData).getPaymentId() != null) {
            rows++;
            isPaymentIdAvailable = true;
        }
        addTitledGroupBg(gridPane, ++rowIndex, rows, "Contract");
        addLabelTextFieldWithCopyIcon(gridPane, rowIndex, "Offer ID:", offer.getId(),
                Layout.FIRST_ROW_DISTANCE).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++rowIndex, "Offer date:", formatter.formatDateTime(offer.getDate()));
        addLabelTextField(gridPane, ++rowIndex, "Trade date:", formatter.formatDateTime(dispute.getTradeDate()));
        addLabelTextField(gridPane, ++rowIndex, "Trade type:", formatter.getDirectionDescription(offer.getDirection()));
        addLabelTextField(gridPane, ++rowIndex, "Price:", formatter.formatFiat(offer.getPrice()) + " " + offer.getCurrencyCode());
        addLabelTextField(gridPane, ++rowIndex, "Trade amount:", formatter.formatCoinWithCode(contract.getTradeAmount()));
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Buyer bitcoin address:",
                contract.getBuyerPayoutAddressString()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Seller bitcoin address:",
                contract.getSellerPayoutAddressString()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Contract hash:",
                Utils.HEX.encode(dispute.getContractHash())).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++rowIndex, "Buyer address:", contract.getBuyerNodeAddress().getFullAddress());
        addLabelTextField(gridPane, ++rowIndex, "Seller address:", contract.getSellerNodeAddress().getFullAddress());
        addLabelTextField(gridPane, ++rowIndex, "Selected arbitrator:", contract.arbitratorNodeAddress.getFullAddress());
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Buyer payment details:",
                BSResources.get(contract.getBuyerPaymentAccountContractData().getPaymentDetails())).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Seller payment details:",
                BSResources.get(sellerPaymentAccountContractData.getPaymentDetails())).second.setMouseTransparent(false);
        if (isPaymentIdAvailable)
            addLabelTextField(gridPane, ++rowIndex, "Seller payment ID:",
                    ((BlockChainAccountContractData) sellerPaymentAccountContractData).getPaymentId());

        if (offer.getAcceptedCountryCodes() != null) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(offer.getAcceptedCountryCodes())) {
                countries = "All Euro countries";
            } else {
                countries = CountryUtil.getCodesString(offer.getAcceptedCountryCodes());
                tooltip = new Tooltip(CountryUtil.getNamesByCodesString(offer.getAcceptedCountryCodes()));
            }
            TextField acceptedCountries = addLabelTextField(gridPane, ++rowIndex, "Accepted taker countries:", countries).second;
            if (tooltip != null) acceptedCountries.setTooltip(new Tooltip());
        }
        //addLabelTextField(gridPane, ++rowIndex, "Buyer Bitsquare account ID:", contract.getBuyerAccountId()).second.setMouseTransparent(false);
        //addLabelTextField(gridPane, ++rowIndex, "Seller Bitsquare account ID:", contract.getSellerAccountId()).second.setMouseTransparent(false);
        addLabelTxIdTextField(gridPane, ++rowIndex, "Offer fee transaction ID:", offer.getOfferFeePaymentTxID());
        addLabelTxIdTextField(gridPane, ++rowIndex, "Trading fee transaction ID:", contract.takeOfferFeeTxID);
        if (dispute.getDepositTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Deposit transaction ID:", dispute.getDepositTxId());
        if (dispute.getPayoutTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Payout transaction ID:", dispute.getPayoutTxId());

        Button cancelButton = addButtonAfterGroup(gridPane, ++rowIndex, "Close");
        //TODO app wide focus
        //cancelButton.requestFocus();
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
            hide();
        });
    }
}
