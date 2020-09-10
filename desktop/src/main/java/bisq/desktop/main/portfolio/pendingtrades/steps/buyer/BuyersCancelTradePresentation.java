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
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;
import bisq.core.trade.BuyerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeCancellationManager;
import bisq.core.util.coin.CoinFormatter;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import javafx.beans.value.ChangeListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles the UI aspects of cancelling a trade
 *
 * todo: handle dispute
 */
@Slf4j
public class BuyersCancelTradePresentation {
    private final Trade trade;
    private final TradeCancellationManager manager;
    private final CoinFormatter formatter;
    private Button cancelTradeButton;
    private Label msgSentStatusLabel;
    private BusyAnimation msgSentBusyAnimation;
    private ChangeListener<BuyerTrade.CancelTradeState> cancelTradeStateListener;
    private ChangeListener<Trade.DisputeState> disputeStateListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    BuyersCancelTradePresentation(Trade trade,
                                  TradeCancellationManager manager,
                                  CoinFormatter formatter) {
        this.trade = trade;
        this.manager = manager;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Life cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(HBox hBox,
                           BusyAnimation msgSentBusyAnimation,
                           Label msgSentStatusLabel) {
        cancelTradeStateListener = (observable, oldValue, newValue) -> onStateChanged(newValue);

        this.msgSentBusyAnimation = msgSentBusyAnimation;
        this.msgSentStatusLabel = msgSentStatusLabel;

        cancelTradeButton = new AutoTooltipButton(Res.get("portfolio.pending.buyer.requestCancelTrade"));
        cancelTradeButton.setOnAction(e -> onRequestCancelTrade());
        hBox.getChildren().add(1, cancelTradeButton);

        disputeStateListener = (observable, oldValue, newValue) -> {
            onDisputeStateChanged();
        };
    }


    public void activate() {
        trade.getBuyersCancelTradeStateProperty().addListener(cancelTradeStateListener);
        onStateChanged(trade.getBuyersCancelTradeStateProperty().get());

        trade.disputeStateProperty().addListener(disputeStateListener);
        onDisputeStateChanged();
    }

    public void deactivate() {
        if (cancelTradeStateListener != null) {
            trade.getBuyersCancelTradeStateProperty().removeListener(cancelTradeStateListener);
        }
        if (disputeStateListener != null) {
            trade.disputeStateProperty().removeListener(disputeStateListener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onRequestCancelTrade() {
        new Popup().width(850)
                .headLine(Res.get("portfolio.pending.buyer.requestCancelTrade"))
                .attention(Res.get("portfolio.pending.buyer.requestCancelTrade.msg",
                        formatter.formatCoinWithCode(manager.getSecurityDepositForRequester())))
                .onAction(this::doRequestCancelTrade)
                .actionButtonText(Res.get("shared.yes"))
                .closeButtonText(Res.get("shared.no"))
                .show();
    }

    private void doRequestCancelTrade() {
        manager.onRequestCancelTrade(trade,
                () -> {
                    log.info("Request cancel trade protocol completed.");
                }, errorMessage -> {
                    msgSentStatusLabel.setText(errorMessage);
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State change handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onDisputeStateChanged() {
        if (trade.isDisputed()) {
            disableButton();
        }
    }

    private void onStateChanged(BuyerTrade.CancelTradeState state) {
        log.error("onStateChanged {} for trade {}", state, trade.getShortId());
        msgSentBusyAnimation.stop();
        msgSentStatusLabel.setText("");

        if (state == null) {
            enableButton();
            return;
        }

        disableButton();
        switch (state) {
            case REQUEST_MSG_SENT:
                msgSentBusyAnimation.play();
                msgSentStatusLabel.setText(Res.get("portfolio.pending.buyer.request.sent"));
                break;
            case REQUEST_MSG_ARRIVED:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.buyer.request.arrived"));
                break;
            case REQUEST_MSG_IN_MAILBOX:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.buyer.request.inMailbox"));
                break;
            case REQUEST_MSG_SEND_FAILED:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.buyer.request.failed"));
                break;

            case RECEIVED_ACCEPTED_MSG:
            case PAYOUT_TX_SEEN_IN_NETWORK:
                new Popup().information(Res.get("portfolio.pending.buyer.response.accepted")).show();
                break;
            case RECEIVED_REJECTED_MSG:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.buyer.response.rejected"));
                break;

            default:
                break;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void disableButton() {
        cancelTradeButton.setDisable(true);
    }

    private void enableButton() {
        if (!trade.isDisputed()) {
            cancelTradeButton.setDisable(false);
        }
    }
}
