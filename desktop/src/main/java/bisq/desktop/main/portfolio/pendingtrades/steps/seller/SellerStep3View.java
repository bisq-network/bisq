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

import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.TextFieldWithCopyIcon;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.BankAccountPayload;
import bisq.core.payment.payload.CashDepositAccountPayload;
import bisq.core.payment.payload.F2FAccountPayload;
import bisq.core.payment.payload.HalCashAccountPayload;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.payment.payload.SepaInstantAccountPayload;
import bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;
import bisq.core.payment.payload.WesternUnionAccountPayload;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple4;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Optional;

import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelTextFieldWithCopyIcon;

public class SellerStep3View extends TradeStepView {

    private Button confirmButton;
    private Label statusLabel;
    private BusyAnimation busyAnimation;
    private Subscription tradeStatePropertySubscription;
    private Timer timeoutTimer;

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
                            confirmButton.setDisable(true);
                            statusLabel.setText(Res.get("shared.sendingConfirmation"));

                            timeoutTimer = UserThread.runAfter(() -> {
                                busyAnimation.stop();
                                confirmButton.setDisable(false);
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
                            confirmButton.setDisable(false);
                            statusLabel.setText("");
                            break;
                        default:
                            log.warn("Unexpected case: State={}, tradeId={} " + state.name(), trade.getId());
                            busyAnimation.stop();
                            confirmButton.setDisable(false);
                            statusLabel.setText(Res.get("shared.sendingConfirmationAgain"));
                            break;
                    }
                } else {
                    log.warn("confirmButton gets disabled because trade contains error message {}", trade.getErrorMessage());
                    confirmButton.setDisable(true);
                    statusLabel.setText("");
                }
            }
        });
    }

    @Override
    public void deactivate() {
        super.deactivate();

        if (tradeStatePropertySubscription != null) {
            tradeStatePropertySubscription.unsubscribe();
            tradeStatePropertySubscription = null;
        }

        busyAnimation.stop();

        if (timeoutTimer != null)
            timeoutTimer.stop();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addContent() {

        gridPane.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);

        addTradeInfoBlock();

        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 3,
                Res.get("portfolio.pending.step3_seller.confirmPaymentReceipt"), Layout.COMPACT_GROUP_DISTANCE);

        TextFieldWithCopyIcon field = addTopLabelTextFieldWithCopyIcon(gridPane, gridRow,
                Res.get("portfolio.pending.step3_seller.amountToReceive"),
                model.getFiatVolume(), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE).second;
        field.setCopyWithoutCurrencyPostFix(true);

        String myPaymentDetails = "";
        String peersPaymentDetails = "";
        String myTitle = "";
        String peersTitle = "";
        boolean isBlockChain = false;
        String nameByCode = CurrencyUtil.getNameByCode(trade.getOffer().getCurrencyCode());
        Contract contract = trade.getContract();
        if (contract != null) {
            PaymentAccountPayload myPaymentAccountPayload = contract.getSellerPaymentAccountPayload();
            PaymentAccountPayload peersPaymentAccountPayload = contract.getBuyerPaymentAccountPayload();
            if (myPaymentAccountPayload instanceof AssetsAccountPayload) {
                myPaymentDetails = ((AssetsAccountPayload) myPaymentAccountPayload).getAddress();
                peersPaymentDetails = ((AssetsAccountPayload) peersPaymentAccountPayload).getAddress();
                myTitle = Res.get("portfolio.pending.step3_seller.yourAddress", nameByCode);
                peersTitle = Res.get("portfolio.pending.step3_seller.buyersAddress", nameByCode);
                isBlockChain = true;
            } else {
                myPaymentDetails = myPaymentAccountPayload.getPaymentDetails();
                peersPaymentDetails = peersPaymentAccountPayload.getPaymentDetails();
                myTitle = Res.get("portfolio.pending.step3_seller.yourAccount");
                peersTitle = Res.get("portfolio.pending.step3_seller.buyersAccount");
            }
        }

        if (!isBlockChain && !trade.getOffer().getPaymentMethod().equals(PaymentMethod.F2F)) {
            addTopLabelTextFieldWithCopyIcon(
                    gridPane, gridRow, 1, Res.get("shared.reasonForPayment"),
                    model.dataModel.getReference(), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);
            GridPane.setRowSpan(titledGroupBg, 4);
        }

        TextFieldWithCopyIcon myPaymentDetailsTextField = addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                0, myTitle, myPaymentDetails).second;
        myPaymentDetailsTextField.setMouseTransparent(false);
        myPaymentDetailsTextField.setTooltip(new Tooltip(myPaymentDetails));

        TextFieldWithCopyIcon peersPaymentDetailsTextField = addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow,
                1, peersTitle, peersPaymentDetails).second;
        peersPaymentDetailsTextField.setMouseTransparent(false);
        peersPaymentDetailsTextField.setTooltip(new Tooltip(peersPaymentDetails));


        Tuple4<Button, BusyAnimation, Label, HBox> tuple = addButtonBusyAnimationLabelAfterGroup(gridPane, ++gridRow,
                Res.get("portfolio.pending.step3_seller.confirmReceipt"));

        GridPane.setColumnSpan(tuple.forth, 2);
        confirmButton = tuple.first;
        confirmButton.setOnAction(e -> onPaymentReceived());
        busyAnimation = tuple.second;
        statusLabel = tuple.third;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected String getInfoText() {
        String currencyCode = model.dataModel.getCurrencyCode();
        if (model.isBlockChainMethod()) {
            return Res.get("portfolio.pending.step3_seller.buyerStartedPayment", Res.get("portfolio.pending.step3_seller.buyerStartedPayment.altcoin", currencyCode));
        } else {
            return Res.get("portfolio.pending.step3_seller.buyerStartedPayment", Res.get("portfolio.pending.step3_seller.buyerStartedPayment.fiat", currencyCode));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setWarningHeadline();
        String substitute = model.isBlockChainMethod() ?
                Res.get("portfolio.pending.step3_seller.warn.part1a", model.dataModel.getCurrencyCode()) :
                Res.get("portfolio.pending.step3_seller.warn.part1b");
        return Res.get("portfolio.pending.step3_seller.warn.part2", substitute, model.getDateForOpenDispute());


    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return Res.get("portfolio.pending.step3_seller.openForDispute");
    }

    @Override
    protected void applyOnDisputeOpened() {
        confirmButton.setDisable(true);
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("PointlessBooleanExpression")
    private void onPaymentReceived() {
        // The confirmPaymentReceived call will trigger the trade protocol to do the payout tx. We want to be sure that we
        // are well connected to the Bitcoin network before triggering the broadcast.
        if (model.dataModel.isReadyForTxBroadcast()) {
            //noinspection UnusedAssignment
            String key = "confirmPaymentReceived";
            //noinspection ConstantConditions
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
                String message = Res.get("portfolio.pending.step3_seller.onPaymentReceived.part1", CurrencyUtil.getNameByCode(model.dataModel.getCurrencyCode()));
                if (!(paymentAccountPayload instanceof AssetsAccountPayload)) {
                    if (!(paymentAccountPayload instanceof WesternUnionAccountPayload) &&
                            !(paymentAccountPayload instanceof HalCashAccountPayload) &&
                            !(paymentAccountPayload instanceof F2FAccountPayload)) {
                        message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.fiat", trade.getShortId());
                    }

                    Optional<String> optionalHolderName = getOptionalHolderName();
                    if (optionalHolderName.isPresent()) {
                        message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.name", optionalHolderName.get());
                    }
                }
                message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.note");
                new Popup<>()
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
        } else {
            model.dataModel.showNotReadyForTxBroadcastPopups();
        }
    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void showPopup() {
        PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
        //noinspection UnusedAssignment
        String key = "confirmPayment" + trade.getId();
        String message = "";
        String tradeVolumeWithCode = model.btcFormatter.formatVolumeWithCode(trade.getTradeVolume());
        String currencyName = CurrencyUtil.getNameByCode(trade.getOffer().getCurrencyCode());
        String part1 = Res.get("portfolio.pending.step3_seller.part", currencyName);
        String id = trade.getShortId();
        if (paymentAccountPayload instanceof AssetsAccountPayload) {
            String address = ((AssetsAccountPayload) paymentAccountPayload).getAddress();
            String explorerOrWalletString = trade.getOffer().getCurrencyCode().equals("XMR") ?
                    Res.get("portfolio.pending.step3_seller.altcoin.wallet", currencyName) :
                    Res.get("portfolio.pending.step3_seller.altcoin.explorer", currencyName);
            //noinspection UnusedAssignment
            message = Res.get("portfolio.pending.step3_seller.altcoin", part1, explorerOrWalletString, address, tradeVolumeWithCode, currencyName);
        } else {
            if (paymentAccountPayload instanceof USPostalMoneyOrderAccountPayload) {
                message = Res.get("portfolio.pending.step3_seller.postal", part1, tradeVolumeWithCode, id);
            } else if (!(paymentAccountPayload instanceof WesternUnionAccountPayload) &&
                    !(paymentAccountPayload instanceof HalCashAccountPayload) &&
                    !(paymentAccountPayload instanceof F2FAccountPayload)) {
                message = Res.get("portfolio.pending.step3_seller.bank", currencyName, tradeVolumeWithCode, id);
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

            Optional<String> optionalHolderName = getOptionalHolderName();
            if (optionalHolderName.isPresent()) {
                //noinspection UnusedAssignment
                message = message + Res.get("portfolio.pending.step3_seller.bankCheck", optionalHolderName.get(), part);
            }
        }
        //noinspection ConstantConditions
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
            DontShowAgainLookup.dontShowAgain(key, true);
            new Popup<>().headLine(Res.get("popup.attention.forTradeWithId", id))
                    .attention(message)
                    .show();
        }
    }

    private void confirmPaymentReceived() {
        confirmButton.setDisable(true);
        busyAnimation.play();
        statusLabel.setText(Res.get("shared.sendingConfirmation"));
        if (!trade.isPayoutPublished())
            trade.setState(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT);

        model.dataModel.onFiatPaymentReceived(() -> {
            // In case the first send failed we got the support button displayed.
            // If it succeeds at a second try we remove the support button again.
            //TODO check for support. in case of a dispute we dont want to hide the button
            //if (notificationGroup != null)
            //   notificationGroup.setButtonVisible(false);
        }, errorMessage -> {
            confirmButton.setDisable(false);
            busyAnimation.stop();
            new Popup<>().warning(Res.get("popup.warning.sendMsgFailed")).show();
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
}


