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

import bisq.desktop.components.BisqTextArea;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.Layout;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeList;
import bisq.core.support.dispute.DisputeManager;
import bisq.core.support.dispute.agent.DisputeAgentLookupMap;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.base.Joiner;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.DisplayUtils.getAccountWitnessDescription;
import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public class ContractWindow extends Overlay<ContractWindow> {
    private final ArbitrationManager arbitrationManager;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final CoinFormatter formatter;
    private Dispute dispute;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ContractWindow(ArbitrationManager arbitrationManager,
                          MediationManager mediationManager,
                          RefundManager refundManager,
                          AccountAgeWitnessService accountAgeWitnessService,
                          @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.formatter = formatter;
        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 1168;
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

        gridPane.getColumnConstraints().get(0).setMinWidth(250d);

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

        int rows = 18;
        if (dispute.getDepositTxSerialized() != null)
            rows++;
        if (dispute.getPayoutTxSerialized() != null)
            rows++;
        if (dispute.getDelayedPayoutTxId() != null)
            rows++;
        if (dispute.getDonationAddressOfDelayedPayoutTx() != null)
            rows++;
        if (showAcceptedCountryCodes)
            rows++;
        if (showAcceptedBanks)
            rows++;

        PaymentAccountPayload sellerPaymentAccountPayload = contract.getSellerPaymentAccountPayload();
        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("contractWindow.title"));
        addConfirmationLabelTextField(gridPane, rowIndex, Res.get("shared.offerId"), offer.getId(),
                Layout.TWICE_FIRST_ROW_DISTANCE);
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("contractWindow.dates"),
                DisplayUtils.formatDateTime(offer.getDate()) + " / " + DisplayUtils.formatDateTime(dispute.getTradeDate()));
        String currencyCode = offer.getCurrencyCode();
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.offerType"),
                DisplayUtils.getDirectionBothSides(offer.getDirection()));
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.tradePrice"),
                FormattingUtils.formatPrice(contract.getTradePrice()));
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.tradeAmount"),
                formatter.formatCoinWithCode(contract.getTradeAmount()));
        addConfirmationLabelTextField(gridPane,
                ++rowIndex,
                VolumeUtil.formatVolumeLabel(currencyCode, ":"),
                VolumeUtil.formatVolumeWithCode(contract.getTradeVolume()));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(offer.getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(offer.getSellerSecurityDeposit());
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.securityDeposit"), securityDeposit);
        addConfirmationLabelTextField(gridPane,
                ++rowIndex,
                Res.get("contractWindow.btcAddresses"),
                contract.getBuyerPayoutAddressString() + " / " + contract.getSellerPayoutAddressString());
        addConfirmationLabelTextField(gridPane,
                ++rowIndex,
                Res.get("contractWindow.onions"),
                contract.getBuyerNodeAddress().getFullAddress() + " / " + contract.getSellerNodeAddress().getFullAddress());

        addConfirmationLabelTextField(gridPane,
                ++rowIndex,
                Res.get("contractWindow.accountAge"),
                getAccountWitnessDescription(accountAgeWitnessService, offer.getPaymentMethod(), contract.getBuyerPaymentAccountPayload(), contract.getBuyerPubKeyRing()) + " / " +
                        getAccountWitnessDescription(accountAgeWitnessService, offer.getPaymentMethod(), contract.getSellerPaymentAccountPayload(), contract.getSellerPubKeyRing()));

        DisputeManager<? extends DisputeList<Dispute>> disputeManager = getDisputeManager(dispute);
        String nrOfDisputesAsBuyer = disputeManager != null ? disputeManager.getNrOfDisputes(true, contract) : "";
        String nrOfDisputesAsSeller = disputeManager != null ? disputeManager.getNrOfDisputes(false, contract) : "";
        addConfirmationLabelTextField(gridPane,
                ++rowIndex,
                Res.get("contractWindow.numDisputes"),
                nrOfDisputesAsBuyer + " / " + nrOfDisputesAsSeller);
        addConfirmationLabelTextField(gridPane,
                ++rowIndex,
                Res.get("shared.paymentDetails", Res.get("shared.buyer")),
                contract.getBuyerPaymentAccountPayload() != null
                        ? contract.getBuyerPaymentAccountPayload().getPaymentDetails()
                        : "NA");
        addConfirmationLabelTextField(gridPane,
                ++rowIndex,
                Res.get("shared.paymentDetails", Res.get("shared.seller")),
                sellerPaymentAccountPayload != null
                        ? sellerPaymentAccountPayload.getPaymentDetails()
                        : "NA");

        String title = "";
        String agentMatrixUserName = "";
        if (dispute.getSupportType() != null) {
            switch (dispute.getSupportType()) {
                case ARBITRATION:
                    title = Res.get("shared.selectedArbitrator");
                    break;
                case MEDIATION:
                    agentMatrixUserName = DisputeAgentLookupMap.getMatrixUserName(contract.getMediatorNodeAddress().getFullAddress());
                    title = Res.get("shared.selectedMediator");
                    break;
                case TRADE:
                    break;
                case REFUND:
                    agentMatrixUserName = DisputeAgentLookupMap.getMatrixUserName(contract.getRefundAgentNodeAddress().getFullAddress());
                    title = Res.get("shared.selectedRefundAgent");
                    break;
            }
        }

        if (disputeManager != null) {
            NodeAddress agentNodeAddress = disputeManager.getAgentNodeAddress(dispute);
            if (agentNodeAddress != null) {
                String value = agentMatrixUserName + " (" + agentNodeAddress.getFullAddress() + ")";
                addConfirmationLabelTextField(gridPane, ++rowIndex, title, value);
            }
        }

        if (showAcceptedCountryCodes) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes)) {
                countries = Res.get("shared.allEuroCountries");
            } else {
                countries = CountryUtil.getCodesString(acceptedCountryCodes);
                tooltip = new Tooltip(CountryUtil.getNamesByCodesString(acceptedCountryCodes));
            }
            addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.acceptedTakerCountries"), countries)
                    .second.setTooltip(tooltip);
        }

        if (showAcceptedBanks) {
            if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK)) {
                addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.bankName"), acceptedBanks.get(0));
            } else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS)) {
                String value = Joiner.on(", ").join(acceptedBanks);
                Tooltip tooltip = new Tooltip(Res.get("shared.acceptedBanks") + value);
                addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.acceptedBanks"), value)
                        .second.setTooltip(tooltip);
            }
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.makerFeeTxId"), offer.getOfferFeePaymentTxId());
        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.takerFeeTxId"), contract.getTakerFeeTxID());

        if (dispute.getDepositTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.depositTransactionId"), dispute.getDepositTxId());

        if (dispute.getDelayedPayoutTxId() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.delayedPayoutTxId"), dispute.getDelayedPayoutTxId());

        if (dispute.getDonationAddressOfDelayedPayoutTx() != null) {
            addLabelExplorerAddressTextField(gridPane, ++rowIndex, Res.get("shared.delayedPayoutTxReceiverAddress"),
                    dispute.getDonationAddressOfDelayedPayoutTx());
        }

        if (dispute.getPayoutTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.payoutTxId"), dispute.getPayoutTxId());

        if (dispute.getContractHash() != null)
            addConfirmationLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.contractHash"),
                    Utils.HEX.encode(dispute.getContractHash())).second.setMouseTransparent(false);

        Button viewContractButton = addConfirmationLabelButton(gridPane, ++rowIndex, Res.get("shared.contractAsJson"),
                Res.get("shared.viewContractAsJson"), 0).second;
        viewContractButton.setDefaultButton(false);
        viewContractButton.setOnAction(e -> {
            TextArea textArea = new BisqTextArea();
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
            viewContractStage.setOpacity(0);
            viewContractStage.show();

            Window window = rootScene.getWindow();
            double titleBarHeight = window.getHeight() - rootScene.getHeight();
            viewContractStage.setX(Math.round(window.getX() + (owner.getWidth() - viewContractStage.getWidth()) / 2) + 200);
            viewContractStage.setY(Math.round(window.getY() + titleBarHeight + (owner.getHeight() - viewContractStage.getHeight()) / 2) + 50);
            // Delay display to next render frame to avoid that the popup is first quickly displayed in default position
            // and after a short moment in the correct position
            UserThread.execute(() -> viewContractStage.setOpacity(1));

            viewContractScene.setOnKeyPressed(ev -> {
                if (ev.getCode() == KeyCode.ESCAPE) {
                    ev.consume();
                    viewContractStage.hide();
                }
            });
        });

        Button closeButton = addButtonAfterGroup(gridPane, ++rowIndex, Res.get("shared.close"));
        GridPane.setColumnSpan(closeButton, 2);
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }

    private DisputeManager<? extends DisputeList<Dispute>> getDisputeManager(Dispute dispute) {
        if (dispute.getSupportType() != null) {
            switch (dispute.getSupportType()) {
                case ARBITRATION:
                    return arbitrationManager;
                case MEDIATION:
                    return mediationManager;
                case TRADE:
                    break;
                case REFUND:
                    return refundManager;
            }
        }
        return null;
    }
}
