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

package io.bitsquare.gui.main.portfolio.pendingtrades;

import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.CompletedView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.StartFiatView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeWizardItem;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitFiatReceivedView;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.WaitTxInBlockchainView;
import io.bitsquare.locale.BSResources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerSubView extends TradeSubView {
    private static final Logger log = LoggerFactory.getLogger(BuyerSubView.class);

    private TradeWizardItem waitTxInBlockchain;
    private TradeWizardItem startFiat;
    private TradeWizardItem waitFiatReceived;
    private TradeWizardItem completed;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerSubView(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void addWizards() {
        waitTxInBlockchain = new TradeWizardItem(WaitTxInBlockchainView.class, "Wait for blockchain confirmation");
        startFiat = new TradeWizardItem(StartFiatView.class, "Start payment");
        waitFiatReceived = new TradeWizardItem(WaitFiatReceivedView.class, "Wait until payment has arrived");
        completed = new TradeWizardItem(CompletedView.class, "Completed");

        leftVBox.getChildren().addAll(waitTxInBlockchain, startFiat, waitFiatReceived, completed);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void applyState(PendingTradesViewModel.ViewState viewState) {
        log.debug("applyState " + viewState);

        waitTxInBlockchain.inactive();
        startFiat.inactive();
        waitFiatReceived.inactive();
        completed.inactive();

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.deactivate();

        switch (viewState) {
            case UNDEFINED:
                break;
            case BUYER_WAIT_TX_CONF:
                showItem(waitTxInBlockchain);

                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoLabelText("Deposit transaction has been published. You need to wait for at least " +
                        "one block chain confirmation.");
                ((WaitTxInBlockchainView) tradeStepDetailsView).setInfoDisplayField("You need to wait for at least one block chain confirmation to" +
                        " be sure that the deposit funding has not been double spent. For higher trade volumes we" +
                        " recommend to wait up to 6 confirmations.");
                break;
            case BUYER_START_PAYMENT:
                waitTxInBlockchain.done();
                showItem(startFiat);
                break;
            case BUYER_WAIT_CONFIRM_PAYMENT_RECEIVED:
                waitTxInBlockchain.done();
                startFiat.done();
                showItem(waitFiatReceived);

                ((WaitFiatReceivedView) tradeStepDetailsView).setInfoLabelText(BSResources.get("Waiting for the Bitcoin sellers confirmation " +
                                "that the {0} payment has arrived.",
                        model.getCurrencyCode()));
                ((WaitFiatReceivedView) tradeStepDetailsView).setInfoDisplayField(BSResources.get("When the confirmation that the {0} payment arrived at " +
                                "the Bitcoin sellers payment account, the payout transaction will be published.",
                        model.getCurrencyCode()));
                break;
            case BUYER_COMPLETED:
                waitTxInBlockchain.done();
                startFiat.done();
                waitFiatReceived.done();
                showItem(completed);

                CompletedView completedView = (CompletedView) tradeStepDetailsView;
                completedView.setBtcTradeAmountLabelText("You have bought:");
                completedView.setFiatTradeAmountLabelText("You have paid:");
                completedView.setBtcTradeAmountTextFieldText(model.getTradeVolume());
                completedView.setFiatTradeAmountTextFieldText(model.getFiatVolume());
                completedView.setFeesTextFieldText(model.getTotalFees());
                completedView.setSecurityDepositTextFieldText(model.getSecurityDeposit());
                completedView.setSummaryInfoDisplayText("Your security deposit has been refunded to you. " +
                        "You can review the details to that trade any time in the closed trades screen.");
                completedView.setWithdrawAmountTextFieldText(model.getPayoutAmount());
                break;
            case MESSAGE_SENDING_FAILED:
                Popups.openWarningPopup("Sending message to trading peer failed.", model.getErrorMessage());
                break;
            case EXCEPTION:
                if (model.getTradeException() != null)
                    Popups.openExceptionPopup(model.getTradeException());
                break;
            default:
                log.warn("unhandled viewState " + viewState);
                break;
        }

        if (tradeStepDetailsView != null)
            tradeStepDetailsView.activate();
    }
}



