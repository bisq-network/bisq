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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.components.TextFieldWithCopyIcon;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.paymentmethods.*;
import io.bitsquare.gui.main.popups.Popup;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.payment.BlockChainAccountContractData;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.Trade;
import io.bitsquare.user.PopupId;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import static io.bitsquare.gui.util.FormBuilder.*;

public class BuyerStep2View extends TradeStepView {

    private Button paymentStartedButton;
    private Label statusLabel;
    private ProgressIndicator statusProgressIndicator;
    private Subscription tradeStatePropertySubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep2View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void activate() {
        super.activate();
        //TODO we get called twice, check why
        if (tradeStatePropertySubscription == null) {
            tradeStatePropertySubscription = EasyBind.subscribe(trade.stateProperty(), state -> {
                if (state.equals(Trade.State.DEPOSIT_CONFIRMED)) {
                    PaymentAccountContractData paymentAccountContractData = model.dataModel.getSellersPaymentAccountContractData();
                    String id = "StartPaymentPopup_" + trade.getId();
                    if (preferences.showAgain(id) && !BitsquareApp.DEV_MODE) {
                        String message = "";
                        if (paymentAccountContractData instanceof BlockChainAccountContractData)
                            message = "Please transfer from your external " +
                                    CurrencyUtil.getNameByCode(trade.getOffer().getCurrencyCode()) + " wallet\n" +
                                    model.formatter.formatFiatWithCode(trade.getTradeVolume()) + " to the bitcoin seller.\n\n" +
                                    "Here are the payment account details of the bitcoin seller:\n" +
                                    "" + paymentAccountContractData.getPaymentDetailsForTradePopup() + ".\n\n" +
                                    "You can copy & paste the receivers address from the main screen after closing that popup.";
                        else if (paymentAccountContractData != null)
                            message = "Please go to your online banking web page and pay\n" +
                                    model.formatter.formatFiatWithCode(trade.getTradeVolume()) + " to the bitcoin seller.\n\n" +
                                    "Here are the payment account details of the bitcoin seller:\n" +
                                    "" + paymentAccountContractData.getPaymentDetailsForTradePopup() + ".\n\n" +
                                    "Please don't forget to add the reference text " + trade.getShortId() +
                                    " so the receiver can assign your payment to this trade.\n" +
                                    "DO NOT use any additional notice in the reference text like " +
                                    "Bitcoin, Btc, Trade or Bitsquare.\n\n" +
                                    "You can copy & paste the values from the main screen after closing that popup.";

                        new Popup().headLine("Notification for trade with ID " + trade.getShortId())
                                .message(message)
                                .closeButtonText("I understand")
                                .dontShowAgainId(id, preferences)
                                .show();
                    }
                }
            });
        }
    }

    @Override
    public void deactivate() {
        super.deactivate();

        removeStatusProgressIndicator();
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

        PaymentAccountContractData paymentAccountContractData = model.dataModel.getSellersPaymentAccountContractData();
        String paymentMethodName = paymentAccountContractData != null ? paymentAccountContractData.getPaymentMethodName() : "";
        TitledGroupBg accountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1,
                "Start payment using " + BSResources.get(paymentAccountContractData.getPaymentMethodName()),
                Layout.GROUP_DISTANCE);
        TextFieldWithCopyIcon field = addLabelTextFieldWithCopyIcon(gridPane, gridRow, "Amount to transfer:",
                model.getFiatAmount(),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        field.setCopyWithoutCurrencyPostFix(true);

        switch (paymentMethodName) {
            case PaymentMethod.OK_PAY_ID:
                gridRow = OKPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.PERFECT_MONEY_ID:
                gridRow = PerfectMoneyForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.SEPA_ID:
                gridRow = SepaForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.NATIONAL_BANK_ID:
                gridRow = NationalBankForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.SAME_BANK_ID:
                gridRow = SameBankForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.SPECIFIC_BANKS_ID:
                gridRow = SpecificBankForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.SWISH_ID:
                gridRow = SwishForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.ALI_PAY_ID:
                gridRow = AliPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.BLOCK_CHAINS_ID:
                gridRow = BlockChainForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            default:
                log.error("Not supported PaymentMethod: " + paymentMethodName);
        }

        if (!(paymentAccountContractData instanceof BlockChainAccountContractData))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Reference text:", model.dataModel.getReference());

        GridPane.setRowSpan(accountTitledGroupBg, gridRow - 3);

        Tuple3<Button, ProgressIndicator, Label> tuple3 = addButtonWithStatusAfterGroup(gridPane, ++gridRow, "Payment started");
        paymentStartedButton = tuple3.first;
        paymentStartedButton.setOnAction(e -> onPaymentStarted());
        statusProgressIndicator = tuple3.second;
        statusLabel = tuple3.third;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setWarningHeadline();
        return "You still have not done your " + model.dataModel.getCurrencyCode() + " payment!\n" +
                "Please note that the trade has to be completed until " +
                model.getOpenDisputeTimeAsFormattedDate() +
                " otherwise the trade will be investigated by the arbitrator.";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return "You have not completed your payment!\n" +
                "The max. period for the trade has elapsed.\n" +
                "\nPlease contact the arbitrator for opening a dispute.";
    }

    @Override
    protected void applyOnDisputeOpened() {
        paymentStartedButton.setDisable(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPaymentStarted() {
        if (model.p2PService.isBootstrapped()) {
            String key = PopupId.PAYMENT_SENT;
            if (preferences.showAgain(key) && !BitsquareApp.DEV_MODE) {
                new Popup().headLine("Confirmation")
                        .message("Did you transfer the payment to your trading partner?")
                        .width(700)
                        .dontShowAgainId(key, preferences)
                        .actionButtonText("Yes I have started the payment")
                        .closeButtonText("No")
                        .onAction(this::confirmPaymentStarted)
                        .show();
            } else {
                confirmPaymentStarted();
            }
        } else {
            new Popup().warning("You need to wait until your client is bootstrapped in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }

    private void confirmPaymentStarted() {
        paymentStartedButton.setDisable(true);
        paymentStartedButton.setMinWidth(130);

        statusProgressIndicator.setVisible(true);
        statusProgressIndicator.setManaged(true);
        statusProgressIndicator.setProgress(-1);

        statusLabel.setWrapText(true);
        statusLabel.setPrefWidth(160);
        statusLabel.setText("Sending message to your trading partner.\n" +
                "Please wait until you get the confirmation that the message has arrived.");

        model.dataModel.onPaymentStarted(() -> {
            // We would not really need an update as the success triggers a screen change
            removeStatusProgressIndicator();
            statusLabel.setText("");

            // In case the first send failed we got the support button displayed. 
            // If it succeeds at a second try we remove the support button again.
            //TODO check for support. in case of a dispute we dont want to hid ethe button
            /*if (notificationGroup != null) {
                notificationGroup.setButtonVisible(false);
            }*/
        }, errorMessage -> {
            removeStatusProgressIndicator();
            statusLabel.setText("Sending message to your trading partner failed.\n" +
                    "Please try again and if it continue to fail report a bug.");
            paymentStartedButton.setDisable(false);
        });
    }

    private void removeStatusProgressIndicator() {
        statusProgressIndicator.setVisible(false);
        statusProgressIndicator.setProgress(0);
        statusProgressIndicator.setManaged(false);
    }

}
