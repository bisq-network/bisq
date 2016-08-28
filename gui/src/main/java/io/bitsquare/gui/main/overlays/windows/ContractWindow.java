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

import com.google.common.base.Joiner;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.offer.Offer;
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
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.ExchangeRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

import static io.bitsquare.gui.util.FormBuilder.*;

public class ContractWindow extends Overlay<ContractWindow> {
    protected static final Logger log = LoggerFactory.getLogger(ContractWindow.class);

    private final BSFormatter formatter;
    private Dispute dispute;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ContractWindow(BSFormatter formatter) {
        this.formatter = formatter;
        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

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

        List<String> acceptedBanks = offer.getAcceptedBankIds();
        boolean showAcceptedBanks = acceptedBanks != null && !acceptedBanks.isEmpty();
        List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
        boolean showAcceptedCountryCodes = acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty();

        int rows = 18;
        if (dispute.getDepositTxSerialized() != null)
            rows++;
        if (dispute.getPayoutTxSerialized() != null)
            rows++;
        if (showAcceptedCountryCodes)
            rows++;
        if (showAcceptedBanks)
            rows++;

        PaymentAccountContractData sellerPaymentAccountContractData = contract.getSellerPaymentAccountContractData();
        addTitledGroupBg(gridPane, ++rowIndex, rows, "Contract");
        addLabelTextFieldWithCopyIcon(gridPane, rowIndex, "Offer ID:", offer.getId(),
                Layout.FIRST_ROW_DISTANCE).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++rowIndex, "Offer date:", formatter.formatDateTime(offer.getDate()));
        addLabelTextField(gridPane, ++rowIndex, "Trade date:", formatter.formatDateTime(dispute.getTradeDate()));
        String currencyCode = offer.getCurrencyCode();
        addLabelTextField(gridPane, ++rowIndex, "Trade type:", formatter.getDirectionBothSides(offer.getDirection(), currencyCode));
        addLabelTextField(gridPane, ++rowIndex, "Trade price:", formatter.formatPrice(contract.getTradePrice()));
        addLabelTextField(gridPane, ++rowIndex, "Trade amount:", formatter.formatCoinWithCode(contract.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex, formatter.formatVolumeLabel(currencyCode, ":"),
                formatter.formatVolumeWithCode(new ExchangeRate(contract.getTradePrice()).coinToFiat(contract.getTradeAmount())));
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC buyer bitcoin address:",
                contract.getBuyerPayoutAddressString()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC seller bitcoin address:",
                contract.getSellerPayoutAddressString()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Contract hash:",
                Utils.HEX.encode(dispute.getContractHash())).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC buyer address:", contract.getBuyerNodeAddress().getFullAddress());
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC seller address:", contract.getSellerNodeAddress().getFullAddress());
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Selected arbitrator:", contract.arbitratorNodeAddress.getFullAddress());
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC buyer payment details:",
                BSResources.get(contract.getBuyerPaymentAccountContractData().getPaymentDetails())).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "BTC seller payment details:",
                BSResources.get(sellerPaymentAccountContractData.getPaymentDetails())).second.setMouseTransparent(false);

        if (showAcceptedCountryCodes) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes)) {
                countries = "All Euro countries";
            } else {
                countries = CountryUtil.getCodesString(acceptedCountryCodes);
                tooltip = new Tooltip(CountryUtil.getNamesByCodesString(acceptedCountryCodes));
            }
            TextField acceptedCountries = addLabelTextField(gridPane, ++rowIndex, "Accepted taker countries:", countries).second;
            if (tooltip != null) acceptedCountries.setTooltip(new Tooltip());
        }

        if (showAcceptedBanks) {
            if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK)) {
                addLabelTextField(gridPane, ++rowIndex, "Bank name:", acceptedBanks.get(0));
            } else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS)) {
                String value = Joiner.on(", ").join(acceptedBanks);
                Tooltip tooltip = new Tooltip("Accepted banks: " + value);
                TextField acceptedBanksTextField = addLabelTextField(gridPane, ++rowIndex, "Accepted banks:", value).second;
                acceptedBanksTextField.setMouseTransparent(false);
                acceptedBanksTextField.setTooltip(tooltip);
            }
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, "Offer fee transaction ID:", offer.getOfferFeePaymentTxID());
        addLabelTxIdTextField(gridPane, ++rowIndex, "Trading fee transaction ID:", contract.takeOfferFeeTxID);
        if (dispute.getDepositTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Deposit transaction ID:", dispute.getDepositTxId());
        if (dispute.getPayoutTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Payout transaction ID:", dispute.getPayoutTxId());

        if (contract != null) {
            Button viewContractButton = addLabelButton(gridPane, ++rowIndex, "Contract in JSON format:", "View contract in JSON format", 0).second;
            viewContractButton.setDefaultButton(false);
            viewContractButton.setOnAction(e -> {
                TextArea textArea = new TextArea();
                textArea.setText(dispute.getContractAsJson());
                textArea.setPrefHeight(50);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setPrefSize(800, 600);

                Scene viewContractScene = new Scene(textArea);
                Stage viewContractStage = new Stage();
                viewContractStage.setTitle("Contract for trade with ID: " + dispute.getShortTradeId());
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

        Button cancelButton = addButtonAfterGroup(gridPane, ++rowIndex, "Close");
        //TODO app wide focus
        //cancelButton.requestFocus();
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
            hide();
        });
    }
}
