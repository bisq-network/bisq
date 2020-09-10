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
import bisq.core.offer.Offer;
import bisq.core.trade.HandleCancelTradeRequestState;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeCancellationManager;
import bisq.core.util.coin.CoinFormatter;

import javafx.beans.value.ChangeListener;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handles the UI aspects of cancelling a trade
 *
 * todo: handle dispute
 */
@Slf4j
public class HandleCancelTradeRequestPresentation {
    private final Trade trade;
    private final Offer offer;
    private final TradeCancellationManager manager;
    private final CoinFormatter formatter;
    private ChangeListener<HandleCancelTradeRequestState> canceledTradeStateListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public HandleCancelTradeRequestPresentation(Trade trade,
                                                TradeCancellationManager manager,
                                                CoinFormatter formatter) {
        this.trade = trade;
        offer = checkNotNull(trade.getOffer());
        this.manager = manager;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Life cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize() {
        canceledTradeStateListener = (observable, oldValue, newValue) -> onCanceledTradeStateChanged(newValue);
    }

    public void activate() {
        trade.getHandleCancelTradeRequestStateProperty().addListener(canceledTradeStateListener);
        onCanceledTradeStateChanged(trade.getHandleCancelTradeRequestStateProperty().get());
    }

    public void deactivate() {
        if (canceledTradeStateListener != null) {
            trade.getHandleCancelTradeRequestStateProperty().removeListener(canceledTradeStateListener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void acceptCancelTradeRequest() {
        manager.acceptCancelTradeRequest(trade,
                () -> {
                }, errorMessage -> {
                });
    }

    private void rejectCancelTradeRequest() {
        manager.rejectCancelTradeRequest(trade,
                () -> {
                }, errorMessage -> {
                });
    }


    private void onCanceledTradeStateChanged(HandleCancelTradeRequestState newValue) {
        log.error("onCanceledTradeStateChanged {} {}", newValue, trade.getId());
        if (newValue == null) {
            return;
        }
        switch (newValue) {
            case REQUEST_MSG_SENT:
                break;
            case REQUEST_MSG_ARRIVED:
                break;
            case REQUEST_MSG_IN_MAILBOX:
                break;
            case REQUEST_MSG_SEND_FAILED:
                break;
            case RECEIVED_CANCEL_REQUEST:
                new Popup().width(850)
                        .attention(Res.get("portfolio.pending.receivedCancelTradeRequestPopup",
                                formatter.formatCoinWithCode(trade.getTradeAmount()),
                                formatter.formatCoinWithCode(manager.getDefaultSecDepositOfAcceptingTrader(trade)),
                                formatter.formatCoinWithCode(manager.getLostSecDepositOfRequestingTrader(trade))))
                        .actionButtonText(Res.get("shared.accept"))
                        .onAction(this::acceptCancelTradeRequest)
                        .secondaryActionButtonText(Res.get("shared.reject"))
                        .onSecondaryAction(this::rejectCancelTradeRequest)
                        .closeButtonText(Res.get("portfolio.pending.doNotDecideYet"))
                        .show();
                break;
            case RECEIVED_ACCEPTED_MSG:
                break;
            case PAYOUT_TX_PUBLISHED:
                break;
            case PAYOUT_TX_PUBLISHED_MSG_SENT:
                break;
            case PAYOUT_TX_PUBLISHED_MSG_ARRIVED:
                break;
            case PAYOUT_TX_PUBLISHED_MSG_IN_MAILBOX:
                break;
            case PAYOUT_TX_PUBLISHED_MSG_SEND_FAILED:
                break;
            case PAYOUT_TX_SEEN_IN_NETWORK:
                break;
            case REQUEST_CANCELED_MSG_SENT:
                break;
            case REQUEST_CANCELED_MSG_ARRIVED:
                break;
            case REQUEST_CANCELED_MSG_IN_MAILBOX:
                break;
            case REQUEST_CANCELED_MSG_SEND_FAILED:
                break;
            case RECEIVED_REJECTED_MSG:
                break;
        }
    }
}
