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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.TextFieldWithCopyIcon;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.paymentmethods.AchTransferForm;
import bisq.desktop.components.paymentmethods.AdvancedCashForm;
import bisq.desktop.components.paymentmethods.AliPayForm;
import bisq.desktop.components.paymentmethods.AmazonGiftCardForm;
import bisq.desktop.components.paymentmethods.AssetsForm;
import bisq.desktop.components.paymentmethods.BizumForm;
import bisq.desktop.components.paymentmethods.CapitualForm;
import bisq.desktop.components.paymentmethods.CashByMailForm;
import bisq.desktop.components.paymentmethods.CashDepositForm;
import bisq.desktop.components.paymentmethods.CelPayForm;
import bisq.desktop.components.paymentmethods.ChaseQuickPayForm;
import bisq.desktop.components.paymentmethods.ClearXchangeForm;
import bisq.desktop.components.paymentmethods.DomesticWireTransferForm;
import bisq.desktop.components.paymentmethods.F2FForm;
import bisq.desktop.components.paymentmethods.FasterPaymentsForm;
import bisq.desktop.components.paymentmethods.HalCashForm;
import bisq.desktop.components.paymentmethods.ImpsForm;
import bisq.desktop.components.paymentmethods.InteracETransferForm;
import bisq.desktop.components.paymentmethods.JapanBankTransferForm;
import bisq.desktop.components.paymentmethods.MoneseForm;
import bisq.desktop.components.paymentmethods.MoneyBeamForm;
import bisq.desktop.components.paymentmethods.MoneyGramForm;
import bisq.desktop.components.paymentmethods.NationalBankForm;
import bisq.desktop.components.paymentmethods.NeftForm;
import bisq.desktop.components.paymentmethods.NequiForm;
import bisq.desktop.components.paymentmethods.PaxumForm;
import bisq.desktop.components.paymentmethods.PayseraForm;
import bisq.desktop.components.paymentmethods.PaytmForm;
import bisq.desktop.components.paymentmethods.PerfectMoneyForm;
import bisq.desktop.components.paymentmethods.PixForm;
import bisq.desktop.components.paymentmethods.PopmoneyForm;
import bisq.desktop.components.paymentmethods.PromptPayForm;
import bisq.desktop.components.paymentmethods.RevolutForm;
import bisq.desktop.components.paymentmethods.RtgsForm;
import bisq.desktop.components.paymentmethods.SameBankForm;
import bisq.desktop.components.paymentmethods.SatispayForm;
import bisq.desktop.components.paymentmethods.SepaForm;
import bisq.desktop.components.paymentmethods.SepaInstantForm;
import bisq.desktop.components.paymentmethods.SpecificBankForm;
import bisq.desktop.components.paymentmethods.StrikeForm;
import bisq.desktop.components.paymentmethods.SwiftForm;
import bisq.desktop.components.paymentmethods.SwishForm;
import bisq.desktop.components.paymentmethods.TikkieForm;
import bisq.desktop.components.paymentmethods.TransferwiseForm;
import bisq.desktop.components.paymentmethods.TransferwiseUsdForm;
import bisq.desktop.components.paymentmethods.USPostalMoneyOrderForm;
import bisq.desktop.components.paymentmethods.UpholdForm;
import bisq.desktop.components.paymentmethods.UpiForm;
import bisq.desktop.components.paymentmethods.VerseForm;
import bisq.desktop.components.paymentmethods.WeChatPayForm;
import bisq.desktop.components.paymentmethods.WesternUnionForm;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.wallet.BsqWalletView;
import bisq.desktop.main.dao.wallet.send.BsqSendView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.SetXmrTxKeyWindow;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.Layout;
import bisq.desktop.util.Transitions;

import bisq.core.locale.Res;
import bisq.core.monetary.Volume;
import bisq.core.network.MessageState;
import bisq.core.offer.Offer;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.CashByMailAccountPayload;
import bisq.core.payment.payload.CashDepositAccountPayload;
import bisq.core.payment.payload.F2FAccountPayload;
import bisq.core.payment.payload.FasterPaymentsAccountPayload;
import bisq.core.payment.payload.HalCashAccountPayload;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SwiftAccountPayload;
import bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;
import bisq.core.payment.payload.WesternUnionAccountPayload;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.util.VolumeUtil;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple4;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private AutoTooltipButton fillBsqButton;

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
                                statusLabel.setText(Res.get("shared.sendingConfirmation"));
                                model.setMessageStateProperty(MessageState.SENT);
                                timeoutTimer = UserThread.runAfter(() -> {
                                    busyAnimation.stop();
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
                                statusLabel.setText("");
                                model.setMessageStateProperty(MessageState.FAILED);
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

    @Override
    protected void onPendingTradesInitialized() {
        super.onPendingTradesInitialized();
        validatePayoutTx();
        model.checkTakerFeeTx(trade);
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
            case PaymentMethod.SEPA_INSTANT_ID:
                gridRow = SepaInstantForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
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
            case PaymentMethod.JAPAN_BANK_ID:
                gridRow = JapanBankTransferForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.US_POSTAL_MONEY_ORDER_ID:
                gridRow = USPostalMoneyOrderForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.CASH_DEPOSIT_ID:
                gridRow = CashDepositForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.CASH_BY_MAIL_ID:
                gridRow = CashByMailForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
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
                checkNotNull(model.dataModel.getTrade(), "model.dataModel.getTrade() must not be null");
                checkNotNull(model.dataModel.getTrade().getOffer(), "model.dataModel.getTrade().getOffer() must not be null");
                gridRow = F2FForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload, model.dataModel.getTrade().getOffer(), 0);
                break;
            case PaymentMethod.BLOCK_CHAINS_ID:
            case PaymentMethod.BLOCK_CHAINS_INSTANT_ID:
                String labelTitle = Res.get("portfolio.pending.step2_buyer.sellersAddress", getCurrencyName(trade));
                gridRow = AssetsForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload, labelTitle);
                break;
            case PaymentMethod.PROMPT_PAY_ID:
                gridRow = PromptPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.ADVANCED_CASH_ID:
                gridRow = AdvancedCashForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.TRANSFERWISE_ID:
                gridRow = TransferwiseForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.TRANSFERWISE_USD_ID:
                gridRow = TransferwiseUsdForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.PAYSERA_ID:
                gridRow = PayseraForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.PAXUM_ID:
                gridRow = PaxumForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.NEFT_ID:
                gridRow = NeftForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.RTGS_ID:
                gridRow = RtgsForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.IMPS_ID:
                gridRow = ImpsForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.UPI_ID:
                gridRow = UpiForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.PAYTM_ID:
                gridRow = PaytmForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.NEQUI_ID:
                gridRow = NequiForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.BIZUM_ID:
                gridRow = BizumForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.PIX_ID:
                gridRow = PixForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.AMAZON_GIFT_CARD_ID:
                gridRow = AmazonGiftCardForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.CAPITUAL_ID:
                gridRow = CapitualForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.CELPAY_ID:
                gridRow = CelPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.MONESE_ID:
                gridRow = MoneseForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.SATISPAY_ID:
                gridRow = SatispayForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.TIKKIE_ID:
                gridRow = TikkieForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.VERSE_ID:
                gridRow = VerseForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.STRIKE_ID:
                gridRow = StrikeForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.SWIFT_ID:
                gridRow = SwiftForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload, trade);
                break;
            case PaymentMethod.ACH_TRANSFER_ID:
                gridRow = AchTransferForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            case PaymentMethod.DOMESTIC_WIRE_TRANSFER_ID:
                gridRow = DomesticWireTransferForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
                break;
            default:
                log.error("Not supported PaymentMethod: " + paymentMethodId);
        }

        Trade trade = model.getTrade();
        if (trade != null && model.getUser().getPaymentAccounts() != null) {
            Offer offer = trade.getOffer();
            List<PaymentAccount> possiblePaymentAccounts = PaymentAccountUtil.getPossiblePaymentAccounts(offer,
                    model.getUser().getPaymentAccounts(), model.dataModel.getAccountAgeWitnessService());
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

        HBox hBox = tuple3.fourth;
        GridPane.setColumnSpan(hBox, 2);
        confirmButton = tuple3.first;
        confirmButton.setOnAction(e -> onPaymentStarted());
        busyAnimation = tuple3.second;
        statusLabel = tuple3.third;

        if (trade.getOffer().getCurrencyCode().equals("BSQ")) {
            fillBsqButton = new AutoTooltipButton(Res.get("portfolio.pending.step2_buyer.fillInBsqWallet"));
            hBox.getChildren().add(1, fillBsqButton);
            fillBsqButton.setOnAction(e -> {
                AssetsAccountPayload assetsAccountPayload = (AssetsAccountPayload) paymentAccountPayload;
                Tuple2<Volume, String> data = new Tuple2<>(trade.getVolume(), assetsAccountPayload.getAddress());
                model.getNavigation().navigateToWithData(data, MainView.class, DaoView.class, BsqWalletView.class,
                        BsqSendView.class);
            });
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getFirstHalfOverWarnText() {
        return Res.get("portfolio.pending.step2_buyer.warn",
                getCurrencyCode(trade),
                model.getDateForOpenDispute());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getPeriodOverWarnText() {
        return Res.get("portfolio.pending.step2_buyer.openForDispute");
    }

    @Override
    protected void applyOnDisputeOpened() {
    }

    @Override
    protected void updateDisputeState(Trade.DisputeState disputeState) {
        super.updateDisputeState(disputeState);

        confirmButton.setDisable(!trade.confirmPermitted());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPaymentStarted() {
        if (!model.dataModel.isBootstrappedOrShowPopup()) {
            return;
        }

        PaymentAccountPayload sellersPaymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
        Trade trade = checkNotNull(model.dataModel.getTrade(), "trade must not be null");
        if (sellersPaymentAccountPayload instanceof CashDepositAccountPayload) {
            String key = "confirmPaperReceiptSent";
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                Popup popup = new Popup();
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
        } else if (sellersPaymentAccountPayload instanceof WesternUnionAccountPayload) {
            String key = "westernUnionMTCNSent";
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                String email = ((WesternUnionAccountPayload) sellersPaymentAccountPayload).getEmail();
                Popup popup = new Popup();
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
        } else if (sellersPaymentAccountPayload instanceof MoneyGramAccountPayload) {
            String key = "moneyGramMTCNSent";
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                String email = ((MoneyGramAccountPayload) sellersPaymentAccountPayload).getEmail();
                Popup popup = new Popup();
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
        } else if (sellersPaymentAccountPayload instanceof HalCashAccountPayload) {
            String key = "halCashCodeInfo";
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                String mobileNr = ((HalCashAccountPayload) sellersPaymentAccountPayload).getMobileNr();
                Popup popup = new Popup();
                popup.headLine(Res.get("portfolio.pending.step2_buyer.halCashInfo.headline"))
                        .feedback(Res.get("portfolio.pending.step2_buyer.halCashInfo.msg",
                                trade.getShortId(), mobileNr))
                        .onAction(this::showConfirmPaymentStartedPopup)
                        .actionButtonText(Res.get("shared.yes"))
                        .closeButtonText(Res.get("shared.no"))
                        .onClose(popup::hide)
                        .dontShowAgainId(key)
                        .show();
            } else {
                showConfirmPaymentStartedPopup();
            }
        } else if (sellersPaymentAccountPayload instanceof AssetsAccountPayload && isXmrTrade()) {
            SetXmrTxKeyWindow setXmrTxKeyWindow = new SetXmrTxKeyWindow();
            setXmrTxKeyWindow
                    .actionButtonText(Res.get("portfolio.pending.step2_buyer.confirmStart.headline"))
                    .onAction(() -> {
                        String txKey = setXmrTxKeyWindow.getTxKey();
                        String txHash = setXmrTxKeyWindow.getTxHash();
                        if (txKey == null || txHash == null || txKey.isEmpty() || txHash.isEmpty()) {
                            UserThread.runAfter(this::showProofWarningPopup, Transitions.DEFAULT_DURATION, TimeUnit.MILLISECONDS);
                            return;
                        }

                        trade.setCounterCurrencyExtraData(txKey);
                        trade.setCounterCurrencyTxId(txHash);

                        model.dataModel.getTradeManager().requestPersistence();
                        showConfirmPaymentStartedPopup();
                    })
                    .closeButtonText(Res.get("shared.cancel"))
                    .onClose(setXmrTxKeyWindow::hide)
                    .show();
        } else {
            showConfirmPaymentStartedPopup();
        }
    }

    private void showProofWarningPopup() {
        Popup popup = new Popup();
        popup.headLine(Res.get("portfolio.pending.step2_buyer.confirmStart.proof.warningTitle"))
                .confirmation(Res.get("portfolio.pending.step2_buyer.confirmStart.proof.noneProvided"))
                .width(700)
                .actionButtonText(Res.get("portfolio.pending.step2_buyer.confirmStart.warningButton"))
                .onAction(this::showConfirmPaymentStartedPopup)
                .closeButtonText(Res.get("shared.cancel"))
                .onClose(popup::hide)
                .show();
    }

    private void showConfirmPaymentStartedPopup() {
        String key = "confirmPaymentStarted";
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
            Popup popup = new Popup();
            popup.headLine(Res.get("portfolio.pending.step2_buyer.confirmStart.headline"))
                    .confirmation(Res.get("portfolio.pending.step2_buyer.confirmStart.msg", getCurrencyName(trade)))
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
        busyAnimation.play();
        statusLabel.setText(Res.get("shared.sendingConfirmation"));

        //TODO seems this was a hack to enable repeated confirm???
        if (trade.isFiatSent()) {
            trade.setState(Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN);
            model.dataModel.getTradeManager().requestPersistence();
        }

        model.dataModel.onPaymentStarted(() -> {
        }, errorMessage -> {
            busyAnimation.stop();
            new Popup().warning(Res.get("popup.warning.sendMsgFailed")).show();
        });
    }

    private void showPopup() {
        PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
        if (paymentAccountPayload != null) {
            String message = Res.get("portfolio.pending.step2.confReached");
            String refTextWarn = Res.get("portfolio.pending.step2_buyer.refTextWarn");
            String fees = Res.get("portfolio.pending.step2_buyer.fees");
            String id = trade.getShortId();
            String amount = VolumeUtil.formatVolumeWithCode(trade.getVolume());
            if (paymentAccountPayload instanceof AssetsAccountPayload) {
                message += Res.get("portfolio.pending.step2_buyer.altcoin",
                        getCurrencyName(trade),
                        amount);
            } else if (paymentAccountPayload instanceof CashDepositAccountPayload) {
                message += Res.get("portfolio.pending.step2_buyer.cash",
                        amount) +
                        refTextWarn + "\n\n" +
                        fees + "\n\n" +
                        Res.get("portfolio.pending.step2_buyer.cash.extra");
            } else if (paymentAccountPayload instanceof WesternUnionAccountPayload) {
                final String email = ((WesternUnionAccountPayload) paymentAccountPayload).getEmail();
                final String extra = Res.get("portfolio.pending.step2_buyer.westernUnion.extra", email);
                message += Res.get("portfolio.pending.step2_buyer.westernUnion",
                        amount) +
                        extra;
            } else if (paymentAccountPayload instanceof MoneyGramAccountPayload) {
                final String email = ((MoneyGramAccountPayload) paymentAccountPayload).getEmail();
                final String extra = Res.get("portfolio.pending.step2_buyer.moneyGram.extra", email);
                message += Res.get("portfolio.pending.step2_buyer.moneyGram",
                        amount) +
                        extra;
            } else if (paymentAccountPayload instanceof USPostalMoneyOrderAccountPayload) {
                message += Res.get("portfolio.pending.step2_buyer.postal", amount) +
                        refTextWarn;
            } else if (paymentAccountPayload instanceof F2FAccountPayload) {
                message += Res.get("portfolio.pending.step2_buyer.f2f", amount);
            } else if (paymentAccountPayload instanceof FasterPaymentsAccountPayload) {
                message += Res.get("portfolio.pending.step2_buyer.pay", amount) +
                        Res.get("portfolio.pending.step2_buyer.fasterPaymentsHolderNameInfo") + "\n\n" +
                        refTextWarn + "\n\n" +
                        fees;
            } else if (paymentAccountPayload instanceof CashByMailAccountPayload ||
                    paymentAccountPayload instanceof HalCashAccountPayload) {
                message += Res.get("portfolio.pending.step2_buyer.pay", amount);
            } else if (paymentAccountPayload instanceof SwiftAccountPayload) {
                message += Res.get("portfolio.pending.step2_buyer.pay", amount) +
                        refTextWarn + "\n\n" +
                        Res.get("portfolio.pending.step2_buyer.fees.swift");
            } else {
                message += Res.get("portfolio.pending.step2_buyer.pay", amount) +
                        refTextWarn + "\n\n" +
                        fees;
            }

            String key = "startPayment" + trade.getId();
            if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
                DontShowAgainLookup.dontShowAgain(key, true);
                new Popup().headLine(Res.get("popup.attention.forTradeWithId", id))
                        .attention(message)
                        .show();
            }
        }
    }

    private void validatePayoutTx() {
        try {
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    trade.getDelayedPayoutTx(),
                    model.dataModel.daoFacade,
                    model.dataModel.btcWalletService);
        } catch (TradeDataValidation.MissingTxException ignore) {
            // We don't react on those errors as a failed trade might get listed initially but getting removed from the
            // trade manager after initPendingTrades which happens after activate might be called.
        } catch (TradeDataValidation.ValidationException e) {
            if (!model.dataModel.tradeManager.isAllowFaultyDelayedTxs()) {
                new Popup().warning(Res.get("portfolio.pending.invalidTx", e.getMessage())).show();
            }
        }
    }
}
