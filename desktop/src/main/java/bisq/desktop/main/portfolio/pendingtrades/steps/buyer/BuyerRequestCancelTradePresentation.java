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
public class BuyerRequestCancelTradePresentation {
    private final Trade trade;
    private final TradeCancellationManager manager;
    private final CoinFormatter formatter;
    private Button cancelTradeButton;
    private Label msgSentStatusLabel;
    private BusyAnimation msgSentBusyAnimation;
    private ChangeListener<BuyerTrade.CancelTradeState> listener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerRequestCancelTradePresentation(Trade trade,
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
        listener = (observable, oldValue, newValue) -> onStateChanged(newValue);

        this.msgSentBusyAnimation = msgSentBusyAnimation;
        this.msgSentStatusLabel = msgSentStatusLabel;

        cancelTradeButton = new AutoTooltipButton(Res.get("portfolio.pending.cancelTrade"));
        cancelTradeButton.getStyleClass().add("action-button");
        cancelTradeButton.setOnAction(e -> onRequestCancelTrade());
        hBox.getChildren().add(1, cancelTradeButton);
    }

    public void activate() {
        trade.getBuyersCancelTradeStateProperty().addListener(listener);
        onStateChanged(trade.getBuyersCancelTradeStateProperty().get());
    }

    public void deactivate() {
        if (listener != null) {
            trade.getBuyersCancelTradeStateProperty().removeListener(listener);
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
    // UI handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onRequestCancelTrade() {
        new Popup().width(850)
                .attention(Res.get("portfolio.pending.requestCancelTradePopup",
                        formatter.formatCoinWithCode(manager.getSecurityDepositForRequester())))
                .onAction(this::doRequestCancelTrade)
                .actionButtonText(Res.get("shared.yes"))
                .closeButtonText(Res.get("shared.no"))
                .show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void doRequestCancelTrade() {
        manager.onRequestCancelTrade(trade,
                () -> {
                    log.info("Request cancel trade protocol completed.");
                }, errorMessage -> {
                    msgSentStatusLabel.setText(errorMessage);
                });
    }


    private void onStateChanged(BuyerTrade.CancelTradeState state) {
        log.error("onCanceledTradeStateChanged {} {}", state, trade.getId());
        msgSentBusyAnimation.stop();
        msgSentStatusLabel.setText("");

        if (state == null) {
            cancelTradeButton.setDisable(false);
            return;
        }

        cancelTradeButton.setDisable(true);
        switch (state) {
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

            case RECEIVED_ACCEPTED_MSG:
            case PAYOUT_TX_SEEN_IN_NETWORK:
                new Popup().information(Res.get("portfolio.pending.requestGotAccepted")).show();
                break;
            case RECEIVED_REJECTED_MSG:
                msgSentStatusLabel.setText(Res.get("portfolio.pending.requestGotRejected"));
                break;

            default:
                break;
        }
    }
}
