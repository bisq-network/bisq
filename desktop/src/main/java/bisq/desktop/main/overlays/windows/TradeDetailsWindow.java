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
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.support.dispute.agent.DisputeAgentLookupMap;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.txproof.AssetTxProofResult;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Named;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import javafx.geometry.Insets;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.DisplayUtils.getAccountWitnessDescription;
import static bisq.desktop.util.FormBuilder.*;
import static com.google.common.base.Preconditions.checkNotNull;

public class TradeDetailsWindow extends Overlay<TradeDetailsWindow> {
    protected static final Logger log = LoggerFactory.getLogger(TradeDetailsWindow.class);

    private final CoinFormatter formatter;
    private final ArbitrationManager arbitrationManager;
    private final TradeManager tradeManager;
    private final BtcWalletService btcWalletService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private Trade trade;
    private ChangeListener<Number> changeListener;
    private TextArea textArea;
    private String buyersAccountAge;
    private String sellersAccountAge;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeDetailsWindow(@Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                              ArbitrationManager arbitrationManager,
                              TradeManager tradeManager,
                              BtcWalletService btcWalletService,
                              AccountAgeWitnessService accountAgeWitnessService) {
        this.formatter = formatter;
        this.arbitrationManager = arbitrationManager;
        this.tradeManager = tradeManager;
        this.btcWalletService = btcWalletService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        type = Type.Confirmation;
    }

    public void show(Trade trade) {
        this.trade = trade;

        rowIndex = -1;
        width = 918;
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
        String offerType = Res.get("shared.offerType");
        if (tradeManager.isBuyer(offer)) {
            addConfirmationLabelTextField(gridPane, rowIndex, offerType,
                    DisplayUtils.getDirectionForBuyer(myOffer, offer.getCurrencyCode()), Layout.TWICE_FIRST_ROW_DISTANCE);
            fiatDirectionInfo = toSpend;
            btcDirectionInfo = toReceive;
        } else {
            addConfirmationLabelTextField(gridPane, rowIndex, offerType,
                    DisplayUtils.getDirectionForSeller(myOffer, offer.getCurrencyCode()), Layout.TWICE_FIRST_ROW_DISTANCE);
            fiatDirectionInfo = toReceive;
            btcDirectionInfo = toSpend;
        }

        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.btcAmount") + btcDirectionInfo,
                formatter.formatCoinWithCode(trade.getAmount()));
        addConfirmationLabelTextField(gridPane, ++rowIndex,
                VolumeUtil.formatVolumeLabel(offer.getCurrencyCode()) + fiatDirectionInfo,
                VolumeUtil.formatVolumeWithCode(trade.getVolume()));
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.tradePrice"),
                FormattingUtils.formatPrice(trade.getPrice()));
        String paymentMethodText = Res.get(offer.getPaymentMethod().getId());
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.paymentMethod"), paymentMethodText);

        // second group
        rows = 7;
        PaymentAccountPayload buyerPaymentAccountPayload = null;
        PaymentAccountPayload sellerPaymentAccountPayload = null;

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

        boolean showXmrProofResult = checkNotNull(trade.getOffer()).getCurrencyCode().equals("XMR") &&
                trade.getAssetTxProofResult() != null &&
                trade.getAssetTxProofResult() != AssetTxProofResult.UNDEFINED;

        if (trade.getPayoutTx() != null)
            rows++;
        boolean showDisputedTx = arbitrationManager.findOwnDispute(trade.getId()).isPresent() &&
                arbitrationManager.findOwnDispute(trade.getId()).get().getDisputePayoutTxId() != null;
        if (showDisputedTx)
            rows++;
        if (trade.hasFailed())
            rows += 2;
        if (trade.getTradingPeerNodeAddress() != null)
            rows++;
        if (showXmrProofResult)
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("shared.details"), Layout.GROUP_DISTANCE);
        addConfirmationLabelTextField(gridPane, rowIndex, Res.get("shared.tradeId"),
                trade.getId(), Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradeDate"),
                DisplayUtils.formatDateTime(trade.getDate()));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(offer.getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(offer.getSellerSecurityDeposit());
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.securityDeposit"), securityDeposit);

        String txFee = Res.get("shared.makerTxFee", formatter.formatCoinWithCode(offer.getTxFee())) +
                " / " +
                Res.get("shared.takerTxFee", formatter.formatCoinWithCode(trade.getTradeTxFee().multiply(3)));
        addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.txFee"), txFee);

        NodeAddress arbitratorNodeAddress = trade.getArbitratorNodeAddress();
        NodeAddress mediatorNodeAddress = trade.getMediatorNodeAddress();
        if (arbitratorNodeAddress != null && mediatorNodeAddress != null) {
            addConfirmationLabelTextField(gridPane, ++rowIndex,
                    Res.get("tradeDetailsWindow.agentAddresses"),
                    arbitratorNodeAddress.getFullAddress() + " / " + mediatorNodeAddress.getFullAddress());
        }

        if (trade.getTradingPeerNodeAddress() != null)
            addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradingPeersOnion"),
                    trade.getTradingPeerNodeAddress().getFullAddress());

        if (showXmrProofResult) {
            // As the window is already overloaded we replace the tradingPeersPubKeyHash field with the auto-conf state
            // if XMR is the currency
            addConfirmationLabelTextField(gridPane, ++rowIndex,
                    Res.get("portfolio.pending.step3_seller.autoConf.status.label"),
                    GUIUtil.getProofResultAsString(trade.getAssetTxProofResult()));
        }

        if (contract != null) {
            buyersAccountAge = getAccountWitnessDescription(accountAgeWitnessService, offer.getPaymentMethod(), buyerPaymentAccountPayload, contract.getBuyerPubKeyRing());
            sellersAccountAge = getAccountWitnessDescription(accountAgeWitnessService, offer.getPaymentMethod(), sellerPaymentAccountPayload, contract.getSellerPubKeyRing());
            if (buyerPaymentAccountPayload != null) {
                String paymentDetails = buyerPaymentAccountPayload.getPaymentDetails();
                String postFix = " / " + buyersAccountAge;
                addConfirmationLabelTextField(gridPane, ++rowIndex,
                        Res.get("shared.paymentDetails", Res.get("shared.buyer")),
                        paymentDetails + postFix).second.setTooltip(new Tooltip(paymentDetails + postFix));
            }
            if (sellerPaymentAccountPayload != null) {
                String paymentDetails = sellerPaymentAccountPayload.getPaymentDetails();
                String postFix = " / " + sellersAccountAge;
                addConfirmationLabelTextField(gridPane, ++rowIndex,
                        Res.get("shared.paymentDetails", Res.get("shared.seller")),
                        paymentDetails + postFix).second.setTooltip(new Tooltip(paymentDetails + postFix));
            }
            if (buyerPaymentAccountPayload == null && sellerPaymentAccountPayload == null)
                addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("shared.paymentMethod"),
                        Res.get(contract.getPaymentMethodId()));
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.makerFeeTxId"), offer.getOfferFeePaymentTxId());
        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.takerFeeTxId"), trade.getTakerFeeTxId());

        String depositTxId = trade.getDepositTxId();
        Transaction depositTx = trade.getDepositTx();
        String depositTxIdFromTx = depositTx != null ? depositTx.getTxId().toString() : null;
        TxIdTextField depositTxIdTextField = addLabelTxIdTextField(gridPane, ++rowIndex,
                Res.get("shared.depositTransactionId"), depositTxId).second;
        if (depositTxId == null || !depositTxId.equals(depositTxIdFromTx)) {
            depositTxIdTextField.getTextField().setId("address-text-field-error");
            log.error("trade.getDepositTxId() and trade.getDepositTx().getTxId().toString() are not the same. " +
                            "trade.getDepositTxId()={}, trade.getDepositTx().getTxId().toString()={}, depositTx={}",
                    depositTxId, depositTxIdFromTx, depositTx);
        }

        Transaction delayedPayoutTx = trade.getDelayedPayoutTx(btcWalletService);
        String delayedPayoutTxString = delayedPayoutTx != null ? delayedPayoutTx.getTxId().toString() : null;
        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.delayedPayoutTxId"), delayedPayoutTxString);

        if (trade.getPayoutTx() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.payoutTxId"),
                    trade.getPayoutTx().getTxId().toString());
        if (showDisputedTx)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.disputedPayoutTxId"),
                    arbitrationManager.findOwnDispute(trade.getId()).get().getDisputePayoutTxId());

        if (trade.hasFailed()) {
            textArea = addConfirmationLabelTextArea(gridPane, ++rowIndex, Res.get("shared.errorMessage"), "", 0).second;
            textArea.setText(trade.getErrorMessage());
            textArea.setEditable(false);
            //TODO paint red

            IntegerProperty count = new SimpleIntegerProperty(20);
            int rowHeight = 10;
            textArea.prefHeightProperty().bindBidirectional(count);
            changeListener = (ov, old, newVal) -> {
                if (newVal.intValue() > rowHeight)
                    count.setValue(count.get() + newVal.intValue() + 10);
            };
            textArea.scrollTopProperty().addListener(changeListener);
            textArea.setScrollTop(30);

            addConfirmationLabelTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradeState"), trade.getTradePhase().name());
        }

        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++rowIndex,
                Res.get("tradeDetailsWindow.detailData"), Res.get("shared.close"), 15, false);
        Button viewContractButton = tuple.first;
        viewContractButton.setMaxWidth(Region.USE_COMPUTED_SIZE);
        Button closeButton = tuple.second;
        closeButton.setMaxWidth(Region.USE_COMPUTED_SIZE);
        HBox hBox = tuple.third;
        GridPane.setColumnSpan(hBox, 2);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        hBox.getChildren().add(0, spacer);

        if (contract != null) {
            viewContractButton.setOnAction(e -> {
                TextArea textArea = new BisqTextArea();
                textArea.setText(trade.getContractAsJson());
                String data = "Contract as json:\n";
                data += trade.getContractAsJson();
                data += "\n\nOther detail data:";
                data += "\n\nBuyerMultiSigPubKeyHex: " + Utils.HEX.encode(contract.getBuyerMultiSigPubKey());
                data += "\nSellerMultiSigPubKeyHex: " + Utils.HEX.encode(contract.getSellerMultiSigPubKey());
                if (CurrencyUtil.isFiatCurrency(offer.getCurrencyCode())) {
                    data += "\n\nBuyersAccountAge: " + buyersAccountAge;
                    data += "\nSellersAccountAge: " + sellersAccountAge;
                }

                if (depositTx != null) {
                    String depositTxAsHex = Utils.HEX.encode(depositTx.bitcoinSerialize(true));
                    data += "\n\nRaw deposit transaction as hex:\n" + depositTxAsHex;
                }

                data += "\n\nSelected mediator: " + DisputeAgentLookupMap.getMatrixUserName(contract.getMediatorNodeAddress().getFullAddress());
                data += "\nSelected arbitrator (refund agent): " + DisputeAgentLookupMap.getMatrixUserName(contract.getRefundAgentNodeAddress().getFullAddress());

                textArea.setText(data);
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
        }

        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
