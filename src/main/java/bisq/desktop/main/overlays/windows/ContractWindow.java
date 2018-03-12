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

import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.Layout;

import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Contract;

import bisq.common.locale.CountryUtil;
import bisq.common.locale.Res;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import com.google.common.base.Joiner;

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

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public class ContractWindow extends Overlay<ContractWindow> {
    private final DisputeManager disputeManager;
    private final BSFormatter formatter;
    private Dispute dispute;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ContractWindow(DisputeManager disputeManager, BSFormatter formatter) {
        this.disputeManager = disputeManager;
        this.formatter = formatter;
        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 1100;
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
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        Contract contract = dispute.getContract();
        Offer offer = new Offer(contract.getOfferPayload());

        List<String> acceptedBanks = offer.getAcceptedBankIds();
        boolean showAcceptedBanks = acceptedBanks != null && !acceptedBanks.isEmpty();
        List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
        boolean showAcceptedCountryCodes = acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty();

        int rows = 17;
        if (dispute.getDepositTxSerialized() != null)
            rows++;
        if (dispute.getPayoutTxSerialized() != null)
            rows++;
        if (showAcceptedCountryCodes)
            rows++;
        if (showAcceptedBanks)
            rows++;

        PaymentAccountPayload sellerPaymentAccountPayload = contract.getSellerPaymentAccountPayload();
        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("contractWindow.title"));
        addLabelTextFieldWithCopyIcon(gridPane, rowIndex, Res.getWithCol("shared.offerId"), offer.getId(),
                Layout.FIRST_ROW_DISTANCE).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++rowIndex, Res.get("contractWindow.dates"),
                formatter.formatDateTime(offer.getDate()) + " / " + formatter.formatDateTime(dispute.getTradeDate()));
        String currencyCode = offer.getCurrencyCode();
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.offerType"),
                formatter.getDirectionBothSides(offer.getDirection(), currencyCode));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradePrice"),
                formatter.formatPrice(contract.getTradePrice()));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradeAmount"),
                formatter.formatCoinWithCode(contract.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex, formatter.formatVolumeLabel(currencyCode, ":"),
                formatter.formatVolumeWithCode(contract.getTradePrice().getVolumeByAmount(contract.getTradeAmount())));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(offer.getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(offer.getSellerSecurityDeposit());
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.securityDeposit"), securityDeposit);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.btcAddresses"),
                contract.getBuyerPayoutAddressString() + " / " +
                        contract.getSellerPayoutAddressString()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.onions"),
                contract.getBuyerNodeAddress().getFullAddress() + " / " + contract.getSellerNodeAddress().getFullAddress());

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.numDisputes"),
                disputeManager.getNrOfDisputes(true, contract) + " / " + disputeManager.getNrOfDisputes(false, contract));

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("shared.paymentDetails", Res.get("shared.buyer")),
                contract.getBuyerPaymentAccountPayload().getPaymentDetails()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("shared.paymentDetails", Res.get("shared.seller")),
                sellerPaymentAccountPayload.getPaymentDetails()).second.setMouseTransparent(false);

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("shared.arbitrator"), contract.getArbitratorNodeAddress().getFullAddress());

        if (showAcceptedCountryCodes) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes)) {
                countries = Res.getWithCol("shared.allEuroCountries");
            } else {
                countries = CountryUtil.getCodesString(acceptedCountryCodes);
                tooltip = new Tooltip(CountryUtil.getNamesByCodesString(acceptedCountryCodes));
            }
            TextField acceptedCountries = addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.acceptedTakerCountries"), countries).second;
            if (tooltip != null) acceptedCountries.setTooltip(new Tooltip());
        }

        if (showAcceptedBanks) {
            if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK)) {
                addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.bankName"), acceptedBanks.get(0));
            } else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS)) {
                String value = Joiner.on(", ").join(acceptedBanks);
                Tooltip tooltip = new Tooltip(Res.getWithCol("shared.acceptedBanks") + value);
                TextField acceptedBanksTextField = addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.acceptedBanks"), value).second;
                acceptedBanksTextField.setMouseTransparent(false);
                acceptedBanksTextField.setTooltip(tooltip);
            }
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.makerFeeTxId"), offer.getOfferFeePaymentTxId());
        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.takerFeeTxId"), contract.getTakerFeeTxID());
        if (dispute.getDepositTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.getWithCol("shared.depositTransactionId"), dispute.getDepositTxId());
        if (dispute.getPayoutTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.payoutTxId"), dispute.getPayoutTxId());

        if (dispute.getContractHash() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.contractHash"),
                    Utils.HEX.encode(dispute.getContractHash())).second.setMouseTransparent(false);

        Button viewContractButton = addLabelButton(gridPane, ++rowIndex, Res.get("shared.contractAsJson"),
                Res.get("shared.viewContractAsJson"), 0).second;
        viewContractButton.setDefaultButton(false);
        viewContractButton.setOnAction(e -> {
            TextArea textArea = new TextArea();
            String contractAsJson = dispute.getContractAsJson();
            contractAsJson += "\n\nBuyerMultiSigPubKeyHex: " + Utils.HEX.encode(contract.getBuyerMultiSigPubKey());
            contractAsJson += "\nSellerMultiSigPubKeyHex: " + Utils.HEX.encode(contract.getSellerMultiSigPubKey());
            textArea.setText(contractAsJson);
            textArea.setPrefHeight(50);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefSize(800, 600);

            Scene viewContractScene = new Scene(textArea);
            Stage viewContractStage = new Stage();
            viewContractStage.setTitle(Res.get("shared.contract.title", dispute.getShortTradeId()));
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

        Button closeButton = addButtonAfterGroup(gridPane, ++rowIndex, Res.get("shared.close"));
        //TODO app wide focus
        //closeButton.requestFocus();
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
