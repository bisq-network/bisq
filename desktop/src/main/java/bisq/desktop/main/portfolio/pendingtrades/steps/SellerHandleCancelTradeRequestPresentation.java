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

package bisq.desktop.main.portfolio.pendingtrades.steps;

import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;
import bisq.core.trade.HandleCancelTradeRequestState;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeCancellationManager;
import bisq.core.util.coin.CoinFormatter;

import javafx.beans.value.ChangeListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles the UI aspects of cancelling a trade
 *
 * todo: handle dispute
 */
@Slf4j
public class SellerHandleCancelTradeRequestPresentation {
    private final Trade trade;
    private final TradeCancellationManager manager;
    private final CoinFormatter formatter;
    private ChangeListener<HandleCancelTradeRequestState> listener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerHandleCancelTradeRequestPresentation(Trade trade,
                                                      TradeCancellationManager manager,
                                                      CoinFormatter formatter) {
        this.trade = trade;
        this.manager = manager;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Life cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize() {
        listener = (observable, oldValue, newValue) -> onStateChanged(newValue);
    }

    public void activate() {
        trade.getHandleCancelTradeRequestStateProperty().addListener(listener);
        onStateChanged(trade.getHandleCancelTradeRequestStateProperty().get());
    }

    public void deactivate() {
        if (listener != null) {
            trade.getHandleCancelTradeRequestStateProperty().removeListener(listener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAcceptRequest() {
        manager.acceptRequest(trade,
                () -> {
                }, errorMessage -> {
                });
    }

    private void onRejectRequest() {
        manager.rejectRequest(trade,
                () -> {
                }, errorMessage -> {
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onStateChanged(HandleCancelTradeRequestState newValue) {
        log.error("onRequestCancelTradeStateChanged {} {}", newValue, trade.getId());
        if (newValue == null) {
            return;
        }
        switch (newValue) {
            case RECEIVED_REQUEST:
                new Popup().width(850)
                        .attention(Res.get("portfolio.pending.receivedCancelTradeRequestPopup",
                                formatter.formatCoinWithCode(trade.getTradeAmount()),
                                formatter.formatCoinWithCode(manager.getDefaultSecDepositOfAcceptingTrader(trade)),
                                formatter.formatCoinWithCode(manager.getLostSecDepositOfRequestingTrader(trade))))
                        .actionButtonText(Res.get("shared.accept"))
                        .onAction(this::onAcceptRequest)
                        .secondaryActionButtonText(Res.get("shared.reject"))
                        .onSecondaryAction(this::onRejectRequest)
                        .closeButtonText(Res.get("portfolio.pending.doNotDecideYet"))
                        .show();
                break;
            case REQUEST_ACCEPTED_PAYOUT_TX_PUBLISHED:
                break;

            case REQUEST_ACCEPTED_MSG_SENT:
                break;
            case REQUEST_ACCEPTED_MSG_ARRIVED:
                break;
            case REQUEST_ACCEPTED_MSG_IN_MAILBOX:
                break;
            case REQUEST_ACCEPTED_MSG_SEND_FAILED:
                break;

            case REQUEST_REJECTED_MSG_SENT:
                break;
            case REQUEST_REJECTED_MSG_ARRIVED:
                break;
            case REQUEST_REJECTED_MSG_IN_MAILBOX:
                break;
            case REQUEST_REJECTED_MSG_SEND_FAILED:
                break;
        }
    }
}
