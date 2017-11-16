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

package io.bisq.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple3;
import io.bisq.core.payment.payload.*;
import io.bisq.core.trade.Trade;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.gui.components.BusyAnimation;
import io.bisq.gui.components.TextFieldWithCopyIcon;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.components.paymentmethods.*;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bisq.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bisq.gui.util.Layout;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import static io.bisq.gui.util.FormBuilder.*;

public class BuyerStep2View extends TradeStepView {

    private Button confirmButton;
    private Label statusLabel;
    private BusyAnimation busyAnimation;
    private Subscription tradeStatePropertySubscription;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep2View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void activate() {
        super.activate();

        if (timeoutTimer != null)
            timeoutTimer.stop();

        //TODO we get called twice, check why
        if (tradeStatePropertySubscription == null) {
            tradeStatePropertySubscription = EasyBind.subscribe(trade.stateProperty(), state -> {
                if (timeoutTimer != null)
                    timeoutTimer.stop();

                if (trade.isDepositConfirmed() && !trade.isFiatSent()) {
                    showPopup();
                } else if (state.ordinal() <= Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG.ordinal()) {
                    if (!trade.hasFailed()) {
                        switch (state) {
                            case BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED:
                            case BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG:
                                busyAnimation.play();
                                confirmButton.setDisable(true);
                                statusLabel.setText(Res.get("shared.sendingConfirmation"));

                                timeoutTimer = UserThread.runAfter(() -> {
                                    busyAnimation.stop();
                                    confirmButton.setDisable(false);
                                    statusLabel.setText(Res.get("shared.sendingConfirmationAgain"));
                                }, 10);
                                break;
                            case BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG:
                                busyAnimation.stop();
                                statusLabel.setText(Res.get("shared.messageArrived"));
                                break;
                            case BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG:
                                busyAnimation.stop();
                                statusLabel.setText(Res.get("shared.messageStoredInMailbox"));
                                break;
                            case BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG:
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
                        confirmButton.setDisable(true);
                        statusLabel.setText("");
                    }
                }
            });
        }
    }

    @Override
    public void deactivate() {
        super.deactivate();

        busyAnimation.stop();

        if (timeoutTimer != null)
            timeoutTimer.stop();

        if (tradeStatePropertySubscription != null) {
            tradeStatePropertySubscription.unsubscribe();
            tradeStatePropertySubscription = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addContent() {
        addTradeInfoBlock();

        PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
        String paymentMethodId = paymentAccountPayload != null ? paymentAccountPayload.getPaymentMethodId() : "";
        TitledGroupBg accountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1,
                Res.get("portfolio.pending.step2_buyer.startPaymentUsing", Res.get(paymentMethodId)),
                Layout.GROUP_DISTANCE);
        TextFieldWithCopyIcon field = addLabelTextFieldWithCopyIcon(gridPane, gridRow, Res.get("portfolio.pending.step2_buyer.amountToTransfer"),
                model.getFiatVolume(),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        field.setCopyWithoutCurrencyPostFix(true);

        switch (paymentMethodId) {
            case PaymentMethod.OK_PAY_ID:
                gridRow = OKPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.PERFECT_MONEY_ID:
                gridRow = PerfectMoneyForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.SEPA_ID:
                gridRow = SepaForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.FASTER_PAYMENTS_ID:
                gridRow = FasterPaymentsForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.NATIONAL_BANK_ID:
                gridRow = NationalBankForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.SAME_BANK_ID:
                gridRow = SameBankForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.SPECIFIC_BANKS_ID:
                gridRow = SpecificBankForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.SWISH_ID:
                gridRow = SwishForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.ALI_PAY_ID:
                gridRow = AliPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.CLEAR_X_CHANGE_ID:
                gridRow = ClearXchangeForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.CHASE_QUICK_PAY_ID:
                gridRow = ChaseQuickPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.INTERAC_E_TRANSFER_ID:
                gridRow = InteracETransferForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID:
                gridRow = USPostalMoneyOrderForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.CASH_DEPOSIT_ID:
                gridRow = CashDepositForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.WESTERN_UNION_ID:
                gridRow = WesternUnionForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.BLOCK_CHAINS_ID:
                String labelTitle = Res.get("portfolio.pending.step2_buyer.sellersAddress",
                        CurrencyUtil.getNameByCode(trade.getOffer().getCurrencyCode()));
                gridRow = CryptoCurrencyForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload, labelTitle);
                break;
            default:
                log.error("Not supported PaymentMethod: " + paymentMethodId);
        }

        if (!(paymentAccountPayload instanceof CryptoCurrencyAccountPayload))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    Res.getWithCol("shared.reasonForPayment"), model.dataModel.getReference());

        GridPane.setRowSpan(accountTitledGroupBg, gridRow - 3);

        Tuple3<Button, BusyAnimation, Label> tuple3 = addButtonBusyAnimationLabelAfterGroup(gridPane, ++gridRow,
                Res.get("portfolio.pending.step2_buyer.paymentStarted"));
        confirmButton = tuple3.first;
        confirmButton.setOnAction(e -> onPaymentStarted());
        busyAnimation = tuple3.second;
        statusLabel = tuple3.third;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setWarningHeadline();
        return Res.get("portfolio.pending.step2_buyer.warn",
                model.dataModel.getCurrencyCode(),
                model.getDateForOpenDispute());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return Res.get("portfolio.pending.step2_buyer.openForDispute");
    }

    @Override
    protected void applyOnDisputeOpened() {
        confirmButton.setDisable(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("PointlessBooleanExpression")
    private void onPaymentStarted() {
        if (model.p2PService.isBootstrapped()) {
            if (model.dataModel.getSellersPaymentAccountPayload() instanceof CashDepositAccountPayload) {
                //noinspection UnusedAssignment
                String key = "confirmPaperReceiptSent";
                //noinspection ConstantConditions
                if (!DevEnv.DEV_MODE && DontShowAgainLookup.showAgain(key)) {
                    Popup popup = new Popup<>();
                    popup.headLine(Res.get("portfolio.pending.step2_buyer.paperReceipt.headline"))
                            .feedback(Res.get("portfolio.pending.step2_buyer.paperReceipt.msg"))
                            .onAction(this::showConfirmPaymentStartedPopup)
                            .closeButtonText(Res.get("shared.no"))
                            .onClose(popup::hide)
                            .dontShowAgainId(key)
                            .show();
                } else {
                    showConfirmPaymentStartedPopup();
                }
            } else if (model.dataModel.getSellersPaymentAccountPayload() instanceof WesternUnionAccountPayload) {
                //noinspection UnusedAssignment
                //noinspection ConstantConditions
                String key = "westernUnionMTCNSent";
                if (!DevEnv.DEV_MODE && DontShowAgainLookup.showAgain(key)) {
                    String email = ((WesternUnionAccountPayload) model.dataModel.getSellersPaymentAccountPayload()).getEmail();
                    Popup popup = new Popup<>();
                    popup.headLine(Res.get("portfolio.pending.step2_buyer.westernUnionMTCNInfo.headline"))
                            .feedback(Res.get("portfolio.pending.step2_buyer.westernUnionMTCNInfo.msg", email))
                            .onAction(this::showConfirmPaymentStartedPopup)
                            .actionButtonText(Res.get("shared.yes"))
                            .closeButtonText(Res.get("shared.no"))
                            .onClose(popup::hide)
                            .dontShowAgainId(key)
                            .show();
                } else {
                    showConfirmPaymentStartedPopup();
                }
            } else {
                showConfirmPaymentStartedPopup();
            }
        } else {
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void showConfirmPaymentStartedPopup() {
        //noinspection UnusedAssignment
        String key = "confirmPaymentStarted";
        //noinspection ConstantConditions
        if (!DevEnv.DEV_MODE && DontShowAgainLookup.showAgain(key)) {
            Popup popup = new Popup<>();
            popup.headLine(Res.get("portfolio.pending.step2_buyer.confirmStart.headline"))
                    .confirmation(Res.get("portfolio.pending.step2_buyer.confirmStart.msg",
                            CurrencyUtil.getNameByCode(trade.getOffer().getCurrencyCode())))
                    .width(700)
                    .actionButtonText(Res.get("portfolio.pending.step2_buyer.confirmStart.yes"))
                    .onAction(this::confirmPaymentStarted)
                    .closeButtonText(Res.get("shared.no"))
                    .onClose(popup::hide)
                    .dontShowAgainId(key)
                    .show();
        } else {
            confirmPaymentStarted();
        }
    }

    private void confirmPaymentStarted() {
        confirmButton.setDisable(true);
        busyAnimation.play();
        statusLabel.setText(Res.get("shared.sendingConfirmation"));
        if (trade.isFiatSent())
            trade.setState(Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN);

        model.dataModel.onPaymentStarted(() -> {
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

    @SuppressWarnings("PointlessBooleanExpression")
    private void showPopup() {
        PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
        if (paymentAccountPayload != null) {
            String paymentDetailsForTradePopup = paymentAccountPayload.getPaymentDetailsForTradePopup();
            String message = Res.get("portfolio.pending.step2.confReached");
            String copyPaste = Res.get("portfolio.pending.step2_buyer.copyPaste");
            String refTextWarn = Res.get("portfolio.pending.step2_buyer.refTextWarn");
            String accountDetails = Res.get("portfolio.pending.step2_buyer.accountDetails");
            String tradeId = Res.get("portfolio.pending.step2_buyer.tradeId");
            String assign = Res.get("portfolio.pending.step2_buyer.assign");
            String fees = Res.get("portfolio.pending.step2_buyer.fees");
            String id = trade.getShortId();
            String paddedId = " " + id + " ";
            String amount = model.btcFormatter.formatVolumeWithCode(trade.getTradeVolume());
            if (paymentAccountPayload instanceof CryptoCurrencyAccountPayload) {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.altcoin",
                        CurrencyUtil.getNameByCode(trade.getOffer().getCurrencyCode()),
                        amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n\n" +
                        copyPaste;
            } else if (paymentAccountPayload instanceof CashDepositAccountPayload) {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.cash",
                        amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n" +
                        copyPaste + "\n\n" +
                        tradeId + paddedId +
                        assign +
                        refTextWarn + "\n\n" +
                        fees + "\n\n" +
                        Res.get("portfolio.pending.step2_buyer.cash.extra");
            } else if (paymentAccountPayload instanceof WesternUnionAccountPayload) {
                final String email = ((WesternUnionAccountPayload) paymentAccountPayload).getEmail();
                final String extra = Res.get("portfolio.pending.step2_buyer.westernUnion.extra", email);
                message += Res.get("portfolio.pending.step2_buyer.westernUnion",
                        amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n" +
                        copyPaste + "\n\n" +
                        extra;
            } else if (paymentAccountPayload instanceof USPostalMoneyOrderAccountPayload) {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.postal", amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n" +
                        copyPaste + "\n\n" +
                        tradeId + paddedId +
                        assign +
                        refTextWarn;
            } else {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.bank", amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n" +
                        copyPaste + "\n\n" +
                        tradeId + paddedId +
                        assign +
                        refTextWarn + "\n\n" +
                        fees;
            }
            //noinspection ConstantConditions,UnusedAssignment
            String key = "startPayment" + trade.getId();
            //noinspection ConstantConditions,ConstantConditions
            if (!DevEnv.DEV_MODE && DontShowAgainLookup.showAgain(key)) {
                DontShowAgainLookup.dontShowAgain(key, true);
                new Popup<>().headLine(Res.get("popup.attention.forTradeWithId", id))
                        .attention(message)
                        .show();
            }
        }
    }
}
