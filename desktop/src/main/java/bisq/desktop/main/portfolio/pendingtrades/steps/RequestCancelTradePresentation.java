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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;
import bisq.core.trade.HandleCancelTradeRequestState;
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
public class RequestCancelTradePresentation {
    private final Trade trade;
    private final TradeCancellationManager manager;
    private final CoinFormatter formatter;
    private Button cancelTradeButton;
    private Label msgSentStatusLabel;
    private BusyAnimation msgSentBusyAnimation;
    private ChangeListener<HandleCancelTradeRequestState> canceledTradeStateListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestCancelTradePresentation(Trade trade,
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
        canceledTradeStateListener = (observable, oldValue, newValue) -> onCanceledTradeStateChanged(newValue);

        this.msgSentBusyAnimation = msgSentBusyAnimation;
        this.msgSentStatusLabel = msgSentStatusLabel;

        cancelTradeButton = new AutoTooltipButton(Res.get("portfolio.pending.cancelTrade"));
        cancelTradeButton.getStyleClass().add("action-button");
        cancelTradeButton.setOnAction(e -> showRequestCancelTradePopup());
        hBox.getChildren().add(1, cancelTradeButton);
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

    public void hideCancelButton() {
        cancelTradeButton.setVisible(false);
        cancelTradeButton.setManaged(false);
    }

    public void setDisable(boolean isDisabled) {
        //todo
        // cancelTradeButton.setDisable(isDisabled);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestCancelTrade() {
        manager.requestCancelTrade(trade,
                () -> {
                }, errorMessage -> {
                });
    }

    private void showRequestCancelTradePopup() {
        new Popup().width(850)
                .attention(Res.get("portfolio.pending.requestCancelTradePopup",
                        formatter.formatCoinWithCode(manager.getSecurityDepositForRequester())))
                .onAction(this::requestCancelTrade)
                .actionButtonText(Res.get("shared.yes"))
                .closeButtonText(Res.get("shared.no"))
                .show();
    }

    private void onCanceledTradeStateChanged(HandleCancelTradeRequestState newValue) {
        log.error("onCanceledTradeStateChanged {} {}", newValue, trade.getId());
        if (newValue == null) {
            cancelTradeButton.setDisable(false);

            return;
        }

        msgSentBusyAnimation.stop();
        msgSentStatusLabel.setText("");

        cancelTradeButton.setDisable(true);
        switch (newValue) {
            case REQUEST_MSG_SENT:
                msgSentBusyAnimation.play();
                msgSentStatusLabel.setText(Res.get("portfolio.pending.requestSent"));
                break;
            case REQUEST_MSG_ARRIVED:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.requestArrived"));
                break;
            case REQUEST_MSG_IN_MAILBOX:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.requestInMailbox"));
                break;
            case REQUEST_MSG_SEND_FAILED:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.requestFailed"));
                break;
            case RECEIVED_CANCEL_REQUEST:
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
                msgSentStatusLabel.setText(Res.get("portfolio.pending.requestGotRejected"));
                break;
        }
    }
}
