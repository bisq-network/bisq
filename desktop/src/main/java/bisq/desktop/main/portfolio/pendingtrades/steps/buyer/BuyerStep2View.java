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

package bisq.desktop.main.portfolio.pendingtrades.steps.buyer;

import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.TextFieldWithCopyIcon;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.paymentmethods.AdvancedCashForm;
import bisq.desktop.components.paymentmethods.AliPayForm;
import bisq.desktop.components.paymentmethods.AssetsForm;
import bisq.desktop.components.paymentmethods.CashDepositForm;
import bisq.desktop.components.paymentmethods.ChaseQuickPayForm;
import bisq.desktop.components.paymentmethods.ClearXchangeForm;
import bisq.desktop.components.paymentmethods.F2FForm;
import bisq.desktop.components.paymentmethods.FasterPaymentsForm;
import bisq.desktop.components.paymentmethods.HalCashForm;
import bisq.desktop.components.paymentmethods.InteracETransferForm;
import bisq.desktop.components.paymentmethods.MoneyBeamForm;
import bisq.desktop.components.paymentmethods.MoneyGramForm;
import bisq.desktop.components.paymentmethods.NationalBankForm;
import bisq.desktop.components.paymentmethods.PerfectMoneyForm;
import bisq.desktop.components.paymentmethods.PopmoneyForm;
import bisq.desktop.components.paymentmethods.PromptPayForm;
import bisq.desktop.components.paymentmethods.RevolutForm;
import bisq.desktop.components.paymentmethods.SameBankForm;
import bisq.desktop.components.paymentmethods.SepaForm;
import bisq.desktop.components.paymentmethods.SpecificBankForm;
import bisq.desktop.components.paymentmethods.SwishForm;
import bisq.desktop.components.paymentmethods.USPostalMoneyOrderForm;
import bisq.desktop.components.paymentmethods.UpholdForm;
import bisq.desktop.components.paymentmethods.WeChatPayForm;
import bisq.desktop.components.paymentmethods.WesternUnionForm;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.network.MessageState;
import bisq.core.offer.Offer;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.CashDepositAccountPayload;
import bisq.core.payment.payload.F2FAccountPayload;
import bisq.core.payment.payload.FasterPaymentsAccountPayload;
import bisq.core.payment.payload.HalCashAccountPayload;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;
import bisq.core.payment.payload.WesternUnionAccountPayload;
import bisq.core.trade.Trade;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple4;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.List;

import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabel;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelTextFieldWithCopyIcon;
import static com.google.common.base.Preconditions.checkNotNull;

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
                                model.setMessageStateProperty(MessageState.SENT);
                                timeoutTimer = UserThread.runAfter(() -> {
                                    busyAnimation.stop();
                                    confirmButton.setDisable(false);
                                    statusLabel.setText(Res.get("shared.sendingConfirmationAgain"));
                                }, 10);
                                break;
                            case BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG:
                                busyAnimation.stop();
                                statusLabel.setText(Res.get("shared.messageArrived"));
                                model.setMessageStateProperty(MessageState.ARRIVED);
                                break;
                            case BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG:
                                busyAnimation.stop();
                                statusLabel.setText(Res.get("shared.messageStoredInMailbox"));
                                model.setMessageStateProperty(MessageState.STORED_IN_MAILBOX);
                                break;
                            case BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG:
                                // We get a popup and the trade closed, so we dont need to show anything here
                                busyAnimation.stop();
                                confirmButton.setDisable(false);
                                statusLabel.setText("");
                                model.setMessageStateProperty(MessageState.FAILED);
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
        gridPane.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);

        addTradeInfoBlock();

        PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
        String paymentMethodId = paymentAccountPayload != null ? paymentAccountPayload.getPaymentMethodId() : "";
        TitledGroupBg accountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 4,
                Res.get("portfolio.pending.step2_buyer.startPaymentUsing", Res.get(paymentMethodId)),
                Layout.COMPACT_GROUP_DISTANCE);
        TextFieldWithCopyIcon field = addTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 0,
                Res.get("portfolio.pending.step2_buyer.amountToTransfer"),
                model.getFiatVolume(),
                Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE).second;
        field.setCopyWithoutCurrencyPostFix(true);

        if (!(paymentAccountPayload instanceof AssetsAccountPayload) &&
                !(paymentAccountPayload instanceof F2FAccountPayload))
            addTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1,
                    Res.get("shared.reasonForPayment"), model.dataModel.getReference(),
                    Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);

        switch (paymentMethodId) {
            case PaymentMethod.UPHOLD_ID:
                gridRow = UpholdForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.MONEY_BEAM_ID:
                gridRow = MoneyBeamForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.POPMONEY_ID:
                gridRow = PopmoneyForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.REVOLUT_ID:
                gridRow = RevolutForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
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
            case PaymentMethod.WECHAT_PAY_ID:
                gridRow = WeChatPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
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
            case PaymentMethod.MONEY_GRAM_ID:
                gridRow = MoneyGramForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.WESTERN_UNION_ID:
                gridRow = WesternUnionForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.HAL_CASH_ID:
                gridRow = HalCashForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.F2F_ID:
                checkNotNull(model.dataModel.getTrade().getOffer(), "model.dataModel.getTrade().getOffer() must not be null");
                gridRow = F2FForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload, model.dataModel.getTrade().getOffer(), 0);
                break;
            case PaymentMethod.BLOCK_CHAINS_ID:
            case PaymentMethod.BLOCK_CHAINS_INSTANT_ID:
                String labelTitle = Res.get("portfolio.pending.step2_buyer.sellersAddress",
                        CurrencyUtil.getNameByCode(trade.getOffer().getCurrencyCode()));
                gridRow = AssetsForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload, labelTitle);
                break;
            case PaymentMethod.PROMPT_PAY_ID:
                gridRow = PromptPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.ADVANCED_CASH_ID:
                gridRow = AdvancedCashForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            default:
                log.error("Not supported PaymentMethod: " + paymentMethodId);
        }

        Trade trade = model.getTrade();
        if (trade != null && model.getUser().getPaymentAccounts() != null) {
            Offer offer = trade.getOffer();
            List<PaymentAccount> possiblePaymentAccounts = PaymentAccountUtil.getPossiblePaymentAccounts(offer,
                    model.getUser().getPaymentAccounts());
            PaymentAccountPayload buyersPaymentAccountPayload = model.dataModel.getBuyersPaymentAccountPayload();
            if (buyersPaymentAccountPayload != null && possiblePaymentAccounts.size() > 1) {
                String id = buyersPaymentAccountPayload.getId();
                possiblePaymentAccounts.stream()
                        .filter(paymentAccount -> paymentAccount.getId().equals(id))
                        .findFirst()
                        .ifPresent(paymentAccount -> {
                            String accountName = paymentAccount.getAccountName();
                            addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, 0,
                                    Res.get("portfolio.pending.step2_buyer.buyerAccount"), accountName);
                        });
            }
        }

        GridPane.setRowSpan(accountTitledGroupBg, gridRow - 1);

        Tuple4<Button, BusyAnimation, Label, HBox> tuple3 = addButtonBusyAnimationLabel(gridPane, ++gridRow, 0,
                Res.get("portfolio.pending.step2_buyer.paymentStarted"), 10);

        GridPane.setColumnSpan(tuple3.forth, 2);
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
                if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
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
                if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
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
            } else if (model.dataModel.getSellersPaymentAccountPayload() instanceof MoneyGramAccountPayload) {
                //noinspection UnusedAssignment
                //noinspection ConstantConditions
                String key = "moneyGramMTCNSent";
                if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                    String email = ((MoneyGramAccountPayload) model.dataModel.getSellersPaymentAccountPayload()).getEmail();
                    Popup popup = new Popup<>();
                    popup.headLine(Res.get("portfolio.pending.step2_buyer.moneyGramMTCNInfo.headline"))
                            .feedback(Res.get("portfolio.pending.step2_buyer.moneyGramMTCNInfo.msg", email))
                            .onAction(this::showConfirmPaymentStartedPopup)
                            .actionButtonText(Res.get("shared.yes"))
                            .closeButtonText(Res.get("shared.no"))
                            .onClose(popup::hide)
                            .dontShowAgainId(key)
                            .show();
                } else {
                    showConfirmPaymentStartedPopup();
                }
            } else if (model.dataModel.getSellersPaymentAccountPayload() instanceof HalCashAccountPayload) {
                //noinspection UnusedAssignment
                //noinspection ConstantConditions
                String key = "halCashCodeInfo";
                if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                    String mobileNr = ((HalCashAccountPayload) model.dataModel.getSellersPaymentAccountPayload()).getMobileNr();
                    Popup popup = new Popup<>();
                    popup.headLine(Res.get("portfolio.pending.step2_buyer.halCashInfo.headline"))
                            .feedback(Res.get("portfolio.pending.step2_buyer.halCashInfo.msg",
                                    model.dataModel.getTrade().getShortId(), mobileNr))
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
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
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
            if (paymentAccountPayload instanceof AssetsAccountPayload) {
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
                        paymentDetailsForTradePopup + ".\n\n" +
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
                        paymentDetailsForTradePopup + ".\n\n" +
                        copyPaste + "\n\n" +
                        extra;
            } else if (paymentAccountPayload instanceof MoneyGramAccountPayload) {
                final String email = ((MoneyGramAccountPayload) paymentAccountPayload).getEmail();
                final String extra = Res.get("portfolio.pending.step2_buyer.moneyGram.extra", email);
                message += Res.get("portfolio.pending.step2_buyer.moneyGram",
                        amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n\n" +
                        copyPaste + "\n\n" +
                        extra;
            } else if (paymentAccountPayload instanceof USPostalMoneyOrderAccountPayload) {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.postal", amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n\n" +
                        copyPaste + "\n\n" +
                        tradeId + paddedId +
                        assign +
                        refTextWarn;
            } else if (paymentAccountPayload instanceof F2FAccountPayload) {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.f2f", amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + "\n\n" +
                        copyPaste;
            } else if (paymentAccountPayload instanceof HalCashAccountPayload) {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.bank", amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n\n" +
                        copyPaste;
            } else if (paymentAccountPayload instanceof FasterPaymentsAccountPayload) {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.bank", amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n\n" +
                        Res.get("portfolio.pending.step2_buyer.fasterPaymentsHolderNameInfo") + "\n\n" +
                        copyPaste + "\n\n" +
                        tradeId + paddedId +
                        assign +
                        refTextWarn + "\n\n" +
                        fees;
            } else {
                //noinspection UnusedAssignment
                message += Res.get("portfolio.pending.step2_buyer.bank", amount) +
                        accountDetails +
                        paymentDetailsForTradePopup + ".\n\n" +
                        copyPaste + "\n\n" +
                        tradeId + paddedId +
                        assign +
                        refTextWarn + "\n\n" +
                        fees;
            }
            //noinspection ConstantConditions,UnusedAssignment
            String key = "startPayment" + trade.getId();
            //noinspection ConstantConditions,ConstantConditions
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                DontShowAgainLookup.dontShowAgain(key, true);
                new Popup<>().headLine(Res.get("popup.attention.forTradeWithId", id))
                        .attention(message)
                        .show();
            }
        }
    }
}
