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

package bisq.desktop.main.portfolio.pendingtrades.steps.seller;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InfoTextField;
import bisq.desktop.components.TextFieldWithCopyIcon;
import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.wallet.BsqWalletView;
import bisq.desktop.main.dao.wallet.tx.BsqTxView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.payment.payload.AmazonGiftCardAccountPayload;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.BankAccountPayload;
import bisq.core.payment.payload.CashByMailAccountPayload;
import bisq.core.payment.payload.CashDepositAccountPayload;
import bisq.core.payment.payload.F2FAccountPayload;
import bisq.core.payment.payload.HalCashAccountPayload;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.payment.payload.SepaInstantAccountPayload;
import bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;
import bisq.core.payment.payload.WesternUnionAccountPayload;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.txproof.AssetTxProofResult;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.util.VolumeUtil;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple4;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.value.ChangeListener;

import java.util.Optional;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;
import static bisq.desktop.util.Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE;
import static bisq.desktop.util.Layout.COMPACT_GROUP_DISTANCE;
import static bisq.desktop.util.Layout.FLOATING_LABEL_DISTANCE;
import static com.google.common.base.Preconditions.checkNotNull;

public class SellerStep3View extends TradeStepView {

    private Button confirmButton;
    private AutoTooltipButton showBsqWallet;
    private Label statusLabel;
    private BusyAnimation busyAnimation;
    private Subscription tradeStatePropertySubscription;
    private Timer timeoutTimer;
    @Nullable
    private InfoTextField assetTxProofResultField;
    @Nullable
    private TxConfidenceIndicator assetTxConfidenceIndicator;
    @Nullable
    private ChangeListener<Number> proofResultListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerStep3View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void activate() {
        super.activate();

        if (timeoutTimer != null)
            timeoutTimer.stop();

        tradeStatePropertySubscription = EasyBind.subscribe(trade.stateProperty(), state -> {
            if (timeoutTimer != null)
                timeoutTimer.stop();

            if (trade.isFiatSent() && !trade.isFiatReceived()) {
                showPopup();
            } else if (trade.isFiatReceived()) {
                if (!trade.hasFailed()) {
                    switch (state) {
                        case SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT:
                        case SELLER_PUBLISHED_PAYOUT_TX:
                        case SELLER_SENT_PAYOUT_TX_PUBLISHED_MSG:
                            busyAnimation.play();
                            statusLabel.setText(Res.get("shared.sendingConfirmation"));

                            timeoutTimer = UserThread.runAfter(() -> {
                                busyAnimation.stop();
                                statusLabel.setText(Res.get("shared.sendingConfirmationAgain"));
                            }, 10);
                            break;
                        case SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG:
                            busyAnimation.stop();
                            statusLabel.setText(Res.get("shared.messageArrived"));
                            break;
                        case SELLER_STORED_IN_MAILBOX_PAYOUT_TX_PUBLISHED_MSG:
                            busyAnimation.stop();
                            statusLabel.setText(Res.get("shared.messageStoredInMailbox"));
                            break;
                        case SELLER_SEND_FAILED_PAYOUT_TX_PUBLISHED_MSG:
                            // We get a popup and the trade closed, so we dont need to show anything here
                            busyAnimation.stop();
                            statusLabel.setText("");
                            break;
                        default:
                            log.warn("Unexpected case: State={}, tradeId={} " + state.name(), trade.getId());
                            busyAnimation.stop();
                            statusLabel.setText(Res.get("shared.sendingConfirmationAgain"));
                            break;
                    }
                } else {
                    log.warn("Trade contains error message {}", trade.getErrorMessage());
                    statusLabel.setText("");
                }
            }
        });

        if (isXmrTrade()) {
            proofResultListener = (observable, oldValue, newValue) -> {
                applyAssetTxProofResult(trade.getAssetTxProofResult());
            };
            trade.getAssetTxProofResultUpdateProperty().addListener(proofResultListener);

            applyAssetTxProofResult(trade.getAssetTxProofResult());
        }
    }

    @Override
    public void deactivate() {
        super.deactivate();

        if (tradeStatePropertySubscription != null) {
            tradeStatePropertySubscription.unsubscribe();
            tradeStatePropertySubscription = null;
        }

        busyAnimation.stop();

        if (timeoutTimer != null) {
            timeoutTimer.stop();
        }

        if (isXmrTrade()) {
            trade.getAssetTxProofResultUpdateProperty().removeListener(proofResultListener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addContent() {
        gridPane.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);

        addTradeInfoBlock();

        addTitledGroupBg(gridPane, ++gridRow, 3,
                Res.get("portfolio.pending.step3_seller.confirmPaymentReceipt"), COMPACT_GROUP_DISTANCE);

        TextFieldWithCopyIcon field = addTopLabelTextFieldWithCopyIcon(gridPane, gridRow,
                Res.get("portfolio.pending.step3_seller.amountToReceive"),
                model.getFiatVolume(), COMPACT_FIRST_ROW_AND_GROUP_DISTANCE).second;
        field.setCopyWithoutCurrencyPostFix(true);

        String myPaymentDetails = "";
        String peersPaymentDetails = "";
        String myTitle = "";
        String peersTitle = "";
        String currencyName = getCurrencyName(trade);
        Contract contract = trade.getContract();
        if (contract != null) {
            PaymentAccountPayload myPaymentAccountPayload = contract.getSellerPaymentAccountPayload();
            PaymentAccountPayload peersPaymentAccountPayload = contract.getBuyerPaymentAccountPayload();

            myPaymentDetails = PaymentAccountUtil.findPaymentAccount(myPaymentAccountPayload, model.getUser())
                    .map(PaymentAccount::getAccountName)
                    .orElse("");

            if (myPaymentAccountPayload instanceof AssetsAccountPayload) {
                if (myPaymentDetails.isEmpty()) {
                    // Not expected
                    myPaymentDetails = ((AssetsAccountPayload) myPaymentAccountPayload).getAddress();
                }
                peersPaymentDetails = peersPaymentAccountPayload != null ?
                        ((AssetsAccountPayload) peersPaymentAccountPayload).getAddress() : "NA";
                myTitle = Res.get("portfolio.pending.step3_seller.yourAddress", currencyName);
                peersTitle = Res.get("portfolio.pending.step3_seller.buyersAddress", currencyName);
            } else {
                if (myPaymentDetails.isEmpty()) {
                    // Not expected
                    myPaymentDetails = myPaymentAccountPayload != null ?
                            myPaymentAccountPayload.getPaymentDetails() : "NA";
                }
                peersPaymentDetails = peersPaymentAccountPayload != null ?
                        peersPaymentAccountPayload.getPaymentDetails() : "NA";
                myTitle = Res.get("portfolio.pending.step3_seller.yourAccount");
                peersTitle = Res.get("portfolio.pending.step3_seller.buyersAccount");
            }
        }

        if (isXmrTrade()) {
            assetTxProofResultField = new InfoTextField();

            Tuple2<Label, VBox> topLabelWithVBox = getTopLabelWithVBox(Res.get("portfolio.pending.step3_seller.autoConf.status.label"), assetTxProofResultField);
            VBox vBox = topLabelWithVBox.second;

            assetTxConfidenceIndicator = new TxConfidenceIndicator();
            assetTxConfidenceIndicator.setId("xmr-confidence");
            assetTxConfidenceIndicator.setProgress(0);
            assetTxConfidenceIndicator.setTooltip(new Tooltip());
            assetTxProofResultField.setContentForInfoPopOver(createPopoverLabel(Res.get("setting.info.msg")));

            HBox.setMargin(assetTxConfidenceIndicator, new Insets(FLOATING_LABEL_DISTANCE, 0, 0, 0));

            HBox hBox = new HBox();
            HBox.setHgrow(vBox, Priority.ALWAYS);
            hBox.setSpacing(10);
            hBox.getChildren().addAll(vBox, assetTxConfidenceIndicator);

            GridPane.setRowIndex(hBox, gridRow);
            GridPane.setColumnIndex(hBox, 1);
            GridPane.setMargin(hBox, new Insets(COMPACT_FIRST_ROW_AND_GROUP_DISTANCE + FLOATING_LABEL_DISTANCE,
                    0,
                    0,
                    0));
            gridPane.getChildren().add(hBox);
        }

        TextFieldWithCopyIcon myPaymentDetailsTextField = addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                0, myTitle, myPaymentDetails).second;
        myPaymentDetailsTextField.setMouseTransparent(false);
        myPaymentDetailsTextField.setTooltip(new Tooltip(myPaymentDetails));

        TextFieldWithCopyIcon peersPaymentDetailsTextField = addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow,
                1, peersTitle, peersPaymentDetails).second;
        peersPaymentDetailsTextField.setMouseTransparent(false);
        peersPaymentDetailsTextField.setTooltip(new Tooltip(peersPaymentDetails));

        String counterCurrencyTxId = trade.getCounterCurrencyTxId();
        String counterCurrencyExtraData = trade.getCounterCurrencyExtraData();
        if (counterCurrencyTxId != null && !counterCurrencyTxId.isEmpty() &&
                counterCurrencyExtraData != null && !counterCurrencyExtraData.isEmpty()) {
            TextFieldWithCopyIcon txHashTextField = addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    0, Res.get("portfolio.pending.step3_seller.xmrTxHash"), counterCurrencyTxId).second;
            txHashTextField.setMouseTransparent(false);
            txHashTextField.setTooltip(new Tooltip(myPaymentDetails));

            TextFieldWithCopyIcon txKeyDetailsTextField = addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow,
                    1, Res.get("portfolio.pending.step3_seller.xmrTxKey"), counterCurrencyExtraData).second;
            txKeyDetailsTextField.setMouseTransparent(false);
            txKeyDetailsTextField.setTooltip(new Tooltip(peersPaymentDetails));
        }

        Tuple4<Button, BusyAnimation, Label, HBox> tuple = addButtonBusyAnimationLabelAfterGroup(gridPane, ++gridRow,
                Res.get("portfolio.pending.step3_seller.confirmReceipt"));

        HBox hBox = tuple.fourth;
        GridPane.setColumnSpan(tuple.fourth, 2);
        confirmButton = tuple.first;
        confirmButton.setOnAction(e -> onPaymentReceived());
        busyAnimation = tuple.second;
        statusLabel = tuple.third;

        if (trade.getOffer().getCurrencyCode().equals("BSQ")) {
            showBsqWallet = new AutoTooltipButton(Res.get("portfolio.pending.step3_seller.showBsqWallet"));
            hBox.getChildren().add(1, showBsqWallet);
            showBsqWallet.setOnAction(e -> {
                model.getNavigation().navigateTo(MainView.class, DaoView.class, BsqWalletView.class,
                        BsqTxView.class);
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoText() {
        String currencyName = getCurrencyName(trade);
        if (model.isBlockChainMethod()) {
            return Res.get("portfolio.pending.step3_seller.buyerStartedPayment", Res.get("portfolio.pending.step3_seller.buyerStartedPayment.altcoin", currencyName));
        } else {
            return Res.get("portfolio.pending.step3_seller.buyerStartedPayment", Res.get("portfolio.pending.step3_seller.buyerStartedPayment.fiat", currencyName));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getFirstHalfOverWarnText() {
        String substitute = model.isBlockChainMethod() ?
                Res.get("portfolio.pending.step3_seller.warn.part1a", getCurrencyName(trade)) :
                Res.get("portfolio.pending.step3_seller.warn.part1b");
        return Res.get("portfolio.pending.step3_seller.warn.part2", substitute);


    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getPeriodOverWarnText() {
        return Res.get("portfolio.pending.step3_seller.openForDispute");
    }

    @Override
    protected void applyOnDisputeOpened() {
    }

    @Override
    protected void updateDisputeState(Trade.DisputeState disputeState) {
        super.updateDisputeState(disputeState);

        confirmButton.setDisable(!trade.confirmPermitted());
    }

    @Override
    protected void updateMediationResultState(boolean blockOpeningOfResultAcceptedPopup) {
        super.updateMediationResultState(blockOpeningOfResultAcceptedPopup);

        if (hasSelfAccepted()) {
            confirmButton.setDisable(true);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPaymentReceived() {
        // The confirmPaymentReceived call will trigger the trade protocol to do the payout tx. We want to be sure that we
        // are well connected to the Bitcoin network before triggering the broadcast.
        if (model.dataModel.isReadyForTxBroadcast()) {
            String key = "confirmPaymentReceived";
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
                String message = Res.get("portfolio.pending.step3_seller.onPaymentReceived.part1", getCurrencyName(trade));
                if (!(paymentAccountPayload instanceof AssetsAccountPayload)) {
                    Optional<String> optionalHolderName = getOptionalHolderName();
                    if (optionalHolderName.isPresent()) {
                        message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.name", optionalHolderName.get());
                    }
                }

                message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.note");
                if (model.dataModel.isSignWitnessTrade()) {
                    message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.signer");
                }
                new Popup()
                        .headLine(Res.get("portfolio.pending.step3_seller.onPaymentReceived.confirm.headline"))
                        .confirmation(message)
                        .width(700)
                        .actionButtonText(Res.get("portfolio.pending.step3_seller.onPaymentReceived.confirm.yes"))
                        .onAction(this::confirmPaymentReceived)
                        .closeButtonText(Res.get("shared.cancel"))
                        .show();
            } else {
                confirmPaymentReceived();
            }
        }
    }

    private void showPopup() {
        PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
        String key = "confirmPayment" + trade.getId();
        String message = "";
        String tradeVolumeWithCode = VolumeUtil.formatVolumeWithCode(trade.getVolume());
        String currencyName = getCurrencyName(trade);
        String part1 = Res.get("portfolio.pending.step3_seller.part", currencyName);
        if (paymentAccountPayload instanceof AssetsAccountPayload) {
            String address = ((AssetsAccountPayload) paymentAccountPayload).getAddress();
            String explorerOrWalletString = isXmrTrade() ?
                    Res.get("portfolio.pending.step3_seller.altcoin.wallet", currencyName) :
                    Res.get("portfolio.pending.step3_seller.altcoin.explorer", currencyName);
            message = Res.get("portfolio.pending.step3_seller.altcoin",
                    part1,
                    explorerOrWalletString,
                    address,
                    tradeVolumeWithCode,
                    currencyName);
        } else {
            if (paymentAccountPayload instanceof USPostalMoneyOrderAccountPayload) {
                message = Res.get("portfolio.pending.step3_seller.postal", part1, tradeVolumeWithCode);
            } else if (paymentAccountPayload instanceof CashByMailAccountPayload) {
                message = Res.get("portfolio.pending.step3_seller.cashByMail", part1, tradeVolumeWithCode);
            } else if (!(paymentAccountPayload instanceof WesternUnionAccountPayload) &&
                    !(paymentAccountPayload instanceof HalCashAccountPayload) &&
                    !(paymentAccountPayload instanceof F2FAccountPayload) &&
                    !(paymentAccountPayload instanceof AmazonGiftCardAccountPayload)) {
                message = Res.get("portfolio.pending.step3_seller.bank", currencyName, tradeVolumeWithCode);
            }

            String part = Res.get("portfolio.pending.step3_seller.openDispute");
            if (paymentAccountPayload instanceof CashDepositAccountPayload)
                message = message + Res.get("portfolio.pending.step3_seller.cash", part);
            else if (paymentAccountPayload instanceof WesternUnionAccountPayload)
                message = message + Res.get("portfolio.pending.step3_seller.westernUnion");
            else if (paymentAccountPayload instanceof MoneyGramAccountPayload)
                message = message + Res.get("portfolio.pending.step3_seller.moneyGram");
            else if (paymentAccountPayload instanceof HalCashAccountPayload)
                message = message + Res.get("portfolio.pending.step3_seller.halCash");
            else if (paymentAccountPayload instanceof F2FAccountPayload)
                message = part1;
            else if (paymentAccountPayload instanceof AmazonGiftCardAccountPayload)
                message = Res.get("portfolio.pending.step3_seller.amazonGiftCard");

            Optional<String> optionalHolderName = getOptionalHolderName();
            if (optionalHolderName.isPresent()) {
                message += Res.get("portfolio.pending.step3_seller.bankCheck", optionalHolderName.get(), part);
            }

            if (model.dataModel.isSignWitnessTrade()) {
                message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.signer");
            }
        }
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
            DontShowAgainLookup.dontShowAgain(key, true);
            new Popup().headLine(Res.get("popup.attention.forTradeWithId", trade.getShortId()))
                    .attention(message)
                    .show();
        }
    }

    private void confirmPaymentReceived() {
        log.info("User pressed the [Confirm payment receipt] button for Trade {}", trade.getShortId());
        busyAnimation.play();
        statusLabel.setText(Res.get("shared.sendingConfirmation"));

        model.dataModel.onFiatPaymentReceived(() -> {
        }, errorMessage -> {
            busyAnimation.stop();
            new Popup().warning(Res.get("popup.warning.sendMsgFailed")).show();
        });
    }

    private Optional<String> getOptionalHolderName() {
        Contract contract = trade.getContract();
        if (contract != null) {
            PaymentAccountPayload paymentAccountPayload = contract.getBuyerPaymentAccountPayload();
            if (paymentAccountPayload instanceof BankAccountPayload)
                return Optional.of(((BankAccountPayload) paymentAccountPayload).getHolderName());
            else if (paymentAccountPayload instanceof SepaAccountPayload)
                return Optional.of(((SepaAccountPayload) paymentAccountPayload).getHolderName());
            else if (paymentAccountPayload instanceof SepaInstantAccountPayload)
                return Optional.of(((SepaInstantAccountPayload) paymentAccountPayload).getHolderName());
            else
                return Optional.empty();
        } else {
            return Optional.empty();
        }
    }

    private void applyAssetTxProofResult(@Nullable AssetTxProofResult result) {
        checkNotNull(assetTxProofResultField);
        checkNotNull(assetTxConfidenceIndicator);

        String txt = GUIUtil.getProofResultAsString(result);
        assetTxProofResultField.setText(txt);

        if (result == null) {
            assetTxConfidenceIndicator.setProgress(0);
            return;
        }

        switch (result) {
            case PENDING:
            case COMPLETED:
                if (result.getNumRequiredConfirmations() > 0) {
                    int numRequiredConfirmations = result.getNumRequiredConfirmations();
                    int numConfirmations = result.getNumConfirmations();
                    if (numConfirmations == 0) {
                        assetTxConfidenceIndicator.setProgress(-1);
                    } else {
                        double progress = Math.min(1, (double) numConfirmations / (double) numRequiredConfirmations);
                        assetTxConfidenceIndicator.setProgress(progress);
                        assetTxConfidenceIndicator.getTooltip().setText(
                                Res.get("portfolio.pending.autoConf.blocks",
                                        numConfirmations, numRequiredConfirmations));
                    }
                }
                break;
            default:
                // Set invisible by default
                assetTxConfidenceIndicator.setProgress(0);
                break;
        }
    }

    private Label createPopoverLabel(String text) {
        Label label = new Label(text);
        label.setPrefWidth(600);
        label.setWrapText(true);
        label.setPadding(new Insets(10));
        return label;
    }
}
