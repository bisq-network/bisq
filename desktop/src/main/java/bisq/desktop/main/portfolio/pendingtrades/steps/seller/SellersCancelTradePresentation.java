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

package bisq.desktop.main.portfolio.pendingtrades.steps.seller;

import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;
import bisq.core.trade.SellerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeCancellationManager;
import bisq.core.util.coin.CoinFormatter;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

import javafx.beans.value.ChangeListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles the UI aspects of cancelling a trade
 *
 * todo: handle dispute
 */
@Slf4j
public class SellersCancelTradePresentation {
    private final Trade trade;
    private final TradeCancellationManager manager;
    private final CoinFormatter formatter;
    private TitledGroupBg cancelRequestTitledGroupBg;
    private Label cancelRequestInfoLabel;
    private Button cancelRequestButton;
    private ChangeListener<SellerTrade.CancelTradeState> cancelTradeStateListener;
    private ChangeListener<Trade.DisputeState> disputeStateListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    SellersCancelTradePresentation(Trade trade,
                                   TradeCancellationManager manager,
                                   CoinFormatter formatter) {
        this.trade = trade;
        this.manager = manager;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Life cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initialize(TitledGroupBg cancelRequestTitledGroupBg,
                           Label cancelRequestInfoLabel,
                           Button cancelRequestButton) {
        this.cancelRequestTitledGroupBg = cancelRequestTitledGroupBg;
        this.cancelRequestInfoLabel = cancelRequestInfoLabel;
        this.cancelRequestButton = cancelRequestButton;

        cancelTradeStateListener = (observable, oldValue, newValue) -> onStateChanged(newValue);

        disputeStateListener = (observable, oldValue, newValue) -> {
            onDisputeStateChanged();
        };
    }

    public void activate() {
        cancelRequestButton.setOnAction(e -> showPopup());

        trade.getSellersCancelTradeStateProperty().addListener(cancelTradeStateListener);
        onStateChanged(trade.getSellersCancelTradeStateProperty().get());

        trade.disputeStateProperty().addListener(disputeStateListener);
        onDisputeStateChanged();
    }

    public void deactivate() {
        cancelRequestButton.setOnAction(null);

        if (cancelTradeStateListener != null) {
            trade.getSellersCancelTradeStateProperty().removeListener(cancelTradeStateListener);
        }

        if (disputeStateListener != null) {
            trade.disputeStateProperty().removeListener(disputeStateListener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAcceptRequest() {
        hideButton();
        manager.onAcceptRequest(trade,
                () -> {
                    new Popup().information(Res.get("portfolio.pending.seller.accepted")).show();
                }, errorMessage -> {
                });
    }

    private void onRejectRequest() {
        hideButton();
        manager.onRejectRequest(trade,
                () -> {
                }, errorMessage -> {
                });
    }

    private void onDecideLater() {
        showButton();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // State change handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onDisputeStateChanged() {
        if (trade.isDisputed()) {
            hideButton();
        }
    }

    private void onStateChanged(SellerTrade.CancelTradeState state) {
        log.error("onStateChanged {} for trade {}", state, trade.getShortId());
        if (state == null) {
            hideAll();
            hideButton();
            return;
        }

        showHeaderAndInfoLabel();
        hideButton();

        switch (state) {
            case RECEIVED_REQUEST:
                showPopup();
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
                cancelRequestInfoLabel.setText(Res.get("portfolio.pending.seller.rejectResponse.sent"));
                break;
            case REQUEST_REJECTED_MSG_ARRIVED:
                cancelRequestInfoLabel.setText(Res.get("portfolio.pending.seller.rejectResponse.arrived"));
                break;
            case REQUEST_REJECTED_MSG_IN_MAILBOX:
                cancelRequestInfoLabel.setText(Res.get("portfolio.pending.seller.rejectResponse.inMailbox"));
                break;
            case REQUEST_REJECTED_MSG_SEND_FAILED:
                cancelRequestInfoLabel.setText(Res.get("portfolio.pending.seller.rejectResponse.failed"));
                break;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showPopup() {
        if (!trade.isDisputed()) {
            new Popup().width(850)
                    .attention(Res.get("portfolio.pending.seller.receivedCancelTradeRequest.msg",
                            formatter.formatCoinWithCode(trade.getTradeAmount()),
                            formatter.formatCoinWithCode(manager.getDefaultSecDepositOfAcceptingTrader(trade)),
                            formatter.formatCoinWithCode(manager.getLostSecDepositOfRequestingTrader(trade))))
                    .actionButtonText(Res.get("shared.accept"))
                    .onAction(this::onAcceptRequest)
                    .secondaryActionButtonText(Res.get("shared.reject"))
                    .onSecondaryAction(this::onRejectRequest)
                    .closeButtonText(Res.get("portfolio.pending.seller.decideLater"))
                    .onClose(this::onDecideLater)
                    .show();
        }
    }

    private void showHeaderAndInfoLabel() {
        cancelRequestTitledGroupBg.setVisible(true);
        cancelRequestTitledGroupBg.setManaged(true);
        cancelRequestInfoLabel.setVisible(true);
        cancelRequestInfoLabel.setManaged(true);
    }

    private void hideAll() {
        cancelRequestTitledGroupBg.setVisible(false);
        cancelRequestTitledGroupBg.setManaged(false);
        cancelRequestInfoLabel.setVisible(false);
        cancelRequestInfoLabel.setManaged(false);
        cancelRequestButton.setVisible(false);
        cancelRequestButton.setManaged(false);
    }

    private void hideButton() {
        cancelRequestButton.setVisible(false);
        cancelRequestButton.setManaged(false);
    }

    private void showButton() {
        if (!trade.isDisputed()) {
            cancelRequestButton.setVisible(true);
            cancelRequestButton.setManaged(true);
        }
    }
}
