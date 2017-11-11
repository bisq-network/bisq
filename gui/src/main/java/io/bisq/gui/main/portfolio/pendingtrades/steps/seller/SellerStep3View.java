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

package io.bisq.gui.main.portfolio.pendingtrades.steps.seller;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple3;
import io.bisq.core.payment.payload.*;
import io.bisq.core.trade.Contract;
import io.bisq.core.trade.Trade;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.gui.components.BusyAnimation;
import io.bisq.gui.components.TextFieldWithCopyIcon;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bisq.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bisq.gui.util.Layout;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Optional;

import static io.bisq.gui.util.FormBuilder.*;

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
        addTradeInfoBlock();

        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 3, Res.get("portfolio.pending.step3_seller.confirmPaymentReceipt"), Layout.GROUP_DISTANCE);

        TextFieldWithCopyIcon field = addLabelTextFieldWithCopyIcon(gridPane, gridRow, Res.get("portfolio.pending.step3_seller.amountToReceive"),
                model.getFiatVolume(), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
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
            if (myPaymentAccountPayload instanceof CryptoCurrencyAccountPayload) {
                myPaymentDetails = ((CryptoCurrencyAccountPayload) myPaymentAccountPayload).getAddress();
                peersPaymentDetails = ((CryptoCurrencyAccountPayload) peersPaymentAccountPayload).getAddress();
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

        TextFieldWithCopyIcon myPaymentDetailsTextField = addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, myTitle, myPaymentDetails).second;
        myPaymentDetailsTextField.setMouseTransparent(false);
        myPaymentDetailsTextField.setTooltip(new Tooltip(myPaymentDetails));

        TextFieldWithCopyIcon peersPaymentDetailsTextField = addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, peersTitle, peersPaymentDetails).second;
        peersPaymentDetailsTextField.setMouseTransparent(false);
        peersPaymentDetailsTextField.setTooltip(new Tooltip(peersPaymentDetails));

        if (!isBlockChain) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("shared.reasonForPayment"), model.dataModel.getReference());
            GridPane.setRowSpan(titledGroupBg, 4);
        }

        Tuple3<Button, BusyAnimation, Label> tuple = addButtonBusyAnimationLabelAfterGroup(gridPane, ++gridRow, Res.get("portfolio.pending.step3_seller.confirmReceipt"));
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
        log.debug("onPaymentReceived");
        if (model.p2PService.isBootstrapped()) {
            //noinspection UnusedAssignment
            String key = "confirmPaymentReceived";
            //noinspection ConstantConditions
            if (!DevEnv.DEV_MODE && DontShowAgainLookup.showAgain(key)) {
                PaymentAccountPayload paymentAccountPayload = model.dataModel.getSellersPaymentAccountPayload();
                String message = Res.get("portfolio.pending.step3_seller.onPaymentReceived.part1", CurrencyUtil.getNameByCode(model.dataModel.getCurrencyCode()));
                if (!(paymentAccountPayload instanceof CryptoCurrencyAccountPayload)) {
                    if (!(paymentAccountPayload instanceof WesternUnionAccountPayload))
                        message += Res.get("portfolio.pending.step3_seller.onPaymentReceived.fiat", trade.getShortId());

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
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
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
        if (paymentAccountPayload instanceof CryptoCurrencyAccountPayload) {
            String address = ((CryptoCurrencyAccountPayload) paymentAccountPayload).getAddress();
            //noinspection UnusedAssignment
            message = Res.get("portfolio.pending.step3_seller.altcoin", part1, currencyName, address, tradeVolumeWithCode, currencyName);
        } else {
            if (paymentAccountPayload instanceof USPostalMoneyOrderAccountPayload)
                message = Res.get("portfolio.pending.step3_seller.postal", part1, tradeVolumeWithCode, id);
            else if (!(paymentAccountPayload instanceof WesternUnionAccountPayload))
                message = Res.get("portfolio.pending.step3_seller.bank", currencyName, tradeVolumeWithCode, id);

            String part = Res.get("portfolio.pending.step3_seller.openDispute");
            if (paymentAccountPayload instanceof CashDepositAccountPayload)
                message = message + Res.get("portfolio.pending.step3_seller.cash", part);
            else if (paymentAccountPayload instanceof WesternUnionAccountPayload)
                message = message + Res.get("portfolio.pending.step3_seller.westernUnion", part);

            Optional<String> optionalHolderName = getOptionalHolderName();
            if (optionalHolderName.isPresent()) {
                //noinspection UnusedAssignment
                message = message + Res.get("portfolio.pending.step3_seller.bankCheck", optionalHolderName.get(), part);
            }
        }
        //noinspection ConstantConditions
        if (!DevEnv.DEV_MODE && DontShowAgainLookup.showAgain(key)) {
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
            else
                return Optional.<String>empty();
        } else {
            return Optional.<String>empty();
        }
    }
}


