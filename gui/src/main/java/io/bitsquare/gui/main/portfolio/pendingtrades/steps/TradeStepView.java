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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps;

import io.bitsquare.app.Log;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.common.Clock;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.components.paymentmethods.PaymentMethodForm;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.TradeSubView;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.trade.Trade;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.gui.util.FormBuilder.*;

public abstract class TradeStepView extends AnchorPane {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final PendingTradesViewModel model;
    protected final Trade trade;
    protected final Preferences preferences;
    protected final GridPane gridPane;

    private Subscription disputeStateSubscription;
    private Subscription tradePeriodStateSubscription;
    protected int gridRow = 0;
    protected TitledGroupBg tradeInfoTitledGroupBg;
    private TextField timeLeftTextField;
    private ProgressBar timeLeftProgressBar;
    private TxIdTextField txIdTextField;
    protected TradeSubView.NotificationGroup notificationGroup;
    private Subscription txIdSubscription;
    private Clock.Listener clockListener;
    private ChangeListener<String> errorMessageListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TradeStepView(PendingTradesViewModel model) {
        this.model = model;
        preferences = model.dataModel.preferences;
        trade = model.dataModel.getTrade();
        checkNotNull(trade, "trade must not be null at TradeStepView");

        gridPane = addGridPane(this);

        AnchorPane.setLeftAnchor(this, 0d);
        AnchorPane.setRightAnchor(this, 0d);
        AnchorPane.setTopAnchor(this, -10d);
        AnchorPane.setBottomAnchor(this, 0d);

        addContent();

        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                showSupportFields();
        };
    }

    public void activate() {
        if (txIdTextField != null) {
            if (txIdSubscription != null)
                txIdSubscription.unsubscribe();

            txIdSubscription = EasyBind.subscribe(model.dataModel.txId, id -> txIdTextField.setup(id));
        }
        trade.errorMessageProperty().addListener(errorMessageListener);

        disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(), newValue -> {
            if (newValue != null)
                updateDisputeState(newValue);
        });

        tradePeriodStateSubscription = EasyBind.subscribe(trade.getTradePeriodStateProperty(), newValue -> {
            if (newValue != null)
                updateTradePeriodState(newValue);
        });

        clockListener = new Clock.Listener() {
            @Override
            public void onSecondTick() {
            }

            @Override
            public void onMinuteTick() {
                updateTimeLeft();
            }

            @Override
            public void onMissedSecondTick(long missed) {
            }
        };
        model.clock.addListener(clockListener);
    }

    public void deactivate() {
        Log.traceCall();
        if (txIdSubscription != null)
            txIdSubscription.unsubscribe();

        if (txIdTextField != null)
            txIdTextField.cleanup();

        if (errorMessageListener != null)
            trade.errorMessageProperty().removeListener(errorMessageListener);

        if (disputeStateSubscription != null)
            disputeStateSubscription.unsubscribe();

        if (tradePeriodStateSubscription != null)
            tradePeriodStateSubscription.unsubscribe();

        if (clockListener != null)
            model.clock.removeListener(clockListener);

        if (notificationGroup != null)
            notificationGroup.button.setOnAction(null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void addContent() {
        addTradeInfoBlock();
        addInfoBlock();
    }

    protected void addTradeInfoBlock() {
        tradeInfoTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 4, "Trade information");
        txIdTextField = addLabelTxIdTextField(gridPane, gridRow, "Deposit transaction ID:", Layout.FIRST_ROW_DISTANCE).second;
        if (model.dataModel.txId.get() != null)
            txIdTextField.setup(model.dataModel.txId.get());

        PaymentMethodForm.addAllowedPeriod(gridPane, ++gridRow, model.dataModel.getSellersPaymentAccountContractData(),
                model.getDateForOpenDispute());

        timeLeftTextField = addLabelTextField(gridPane, ++gridRow, "Remaining time:").second;

        timeLeftProgressBar = new ProgressBar(0);
        timeLeftProgressBar.setOpacity(0.7);
        timeLeftProgressBar.setMinHeight(9);
        timeLeftProgressBar.setMaxHeight(9);
        timeLeftProgressBar.setMaxWidth(Double.MAX_VALUE);

        GridPane.setRowIndex(timeLeftProgressBar, ++gridRow);
        GridPane.setColumnIndex(timeLeftProgressBar, 1);
        GridPane.setFillWidth(timeLeftProgressBar, true);
        gridPane.getChildren().add(timeLeftProgressBar);

        updateTimeLeft();
    }

    protected void addInfoBlock() {
        addTitledGroupBg(gridPane, ++gridRow, 1, getInfoBlockTitle(), Layout.GROUP_DISTANCE);
        addMultilineLabel(gridPane, gridRow, getInfoText(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
    }

    protected String getInfoText() {
        return "";
    }

    protected String getInfoBlockTitle() {
        return "";
    }

    private void updateTimeLeft() {
        if (timeLeftTextField != null) {
            String remainingTime = model.getRemainingTradeDurationAsWords();
            timeLeftProgressBar.setProgress(model.getRemainingTradeDurationAsPercentage());
            if (remainingTime != null) {
                timeLeftTextField.setText(remainingTime);
                if (model.showWarning() || model.showDispute()) {
                    timeLeftTextField.setStyle("-fx-text-fill: -bs-error-red");
                    timeLeftProgressBar.setStyle("-fx-accent: -bs-error-red;");
                }
            } else {
                timeLeftTextField.setText("Trade not completed in time (" + model.getDateForOpenDispute() + ")");
                timeLeftTextField.setStyle("-fx-text-fill: -bs-error-red");
                timeLeftProgressBar.setStyle("-fx-accent: -bs-error-red;");
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute/warning label and button
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We have the dispute button and text field on the left side, but we handle the content here as it 
    // is trade state specific
    public void setNotificationGroup(TradeSubView.NotificationGroup notificationGroup) {
        this.notificationGroup = notificationGroup;
    }

    private void showDisputeInfoLabel() {
        if (notificationGroup != null)
            notificationGroup.setLabelAndHeadlineVisible(true);
    }

    private void showOpenDisputeButton() {
        if (notificationGroup != null) {
            notificationGroup.setButtonVisible(true);
            notificationGroup.button.setOnAction(e -> {
                notificationGroup.button.setDisable(true);
                onDisputeOpened();
                model.dataModel.onOpenDispute();
            });
        }
    }

    protected void setWarningHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText("Warning");
        }
    }

    protected void setInformationHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText("Notification");
        }
    }

    protected void setOpenDisputeHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText("Open a dispute");
        }
    }

    protected void setDisputeOpenedHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText("Dispute opened");
        }
    }

    protected void setRequestSupportHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText("Open support ticket");
        }
    }

    protected void setSupportOpenedHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText("Support ticket opened");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Support
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showSupportFields() {
        if (notificationGroup != null) {
            notificationGroup.button.setText("Request support");
            notificationGroup.button.setId("open-support-button");
            notificationGroup.button.setOnAction(e -> model.dataModel.onOpenSupportTicket());
        }
        new Popup().warning(trade.errorMessageProperty().getValue()
                + "\n\nPlease report the problem to your arbitrator.\n\n" +
                "He will forward the information to the developers to investigate the problem.\n" +
                "After the problem has be analyzed you will get back all the funds if funds was locked.\n" +
                "There will be no arbitration fee charged in case of a software bug.")
                .show();

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showWarning() {
        showDisputeInfoLabel();

        if (notificationGroup != null)
            notificationGroup.label.setText(getWarningText());
    }

    private void removeWarning() {
        hideNotificationGroup();
    }

    protected String getWarningText() {
        return "";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onOpenForDispute() {
        showDisputeInfoLabel();
        showOpenDisputeButton();
        setOpenDisputeHeadline();

        if (notificationGroup != null)
            notificationGroup.label.setText(getOpenForDisputeText());
    }

    private void onDisputeOpened() {
        showDisputeInfoLabel();
        showOpenDisputeButton();
        applyOnDisputeOpened();
        setDisputeOpenedHeadline();

        if (notificationGroup != null)
            notificationGroup.button.setDisable(true);
    }

    protected String getOpenForDisputeText() {
        return "";
    }

    protected void applyOnDisputeOpened() {
    }

    protected void hideNotificationGroup() {
        notificationGroup.setLabelAndHeadlineVisible(false);
        notificationGroup.setButtonVisible(false);
    }

    private void updateDisputeState(Trade.DisputeState disputeState) {
        Optional<Dispute> ownDispute;
        switch (disputeState) {
            case NONE:
                break;
            case DISPUTE_REQUESTED:
                onDisputeOpened();
                ownDispute = model.dataModel.disputeManager.findOwnDispute(trade.getId());
                ownDispute.ifPresent(dispute -> {
                    String msg;
                    if (dispute.isSupportTicket()) {
                        setSupportOpenedHeadline();
                        msg = "You opened already a support ticket.\n" +
                                "Please communicate in the \"Support\" screen with the arbitrator.";
                    } else {
                        setDisputeOpenedHeadline();
                        msg = "You opened already a dispute.\n" +
                                "Please communicate in the \"Support\" screen with the arbitrator.";
                    }
                    if (notificationGroup != null)
                        notificationGroup.label.setText(msg);
                });

                break;
            case DISPUTE_STARTED_BY_PEER:
                onDisputeOpened();
                ownDispute = model.dataModel.disputeManager.findOwnDispute(trade.getId());
                ownDispute.ifPresent(dispute -> {
                    String msg;
                    if (dispute.isSupportTicket()) {
                        setSupportOpenedHeadline();
                        msg = "Your trading peer opened a support ticket due technical problems.\n" +
                                "Please communicate in the \"Support\" screen with the arbitrator.";
                    } else {
                        setDisputeOpenedHeadline();
                        msg = "Your trading peer opened a dispute.\n" +
                                "Please communicate in the \"Support\" screen with the arbitrator.";
                    }
                    if (notificationGroup != null)
                        notificationGroup.label.setText(msg);
                });
                break;
            case DISPUTE_CLOSED:
                break;
        }
    }

    private void updateTradePeriodState(Trade.TradePeriodState tradePeriodState) {
        if (trade.getDisputeState() != Trade.DisputeState.DISPUTE_REQUESTED &&
                trade.getDisputeState() != Trade.DisputeState.DISPUTE_STARTED_BY_PEER) {
            switch (tradePeriodState) {
                case NORMAL:
                    break;
                case HALF_REACHED:
                    if (trade.getState().getPhase().ordinal() < Trade.Phase.FIAT_RECEIVED.ordinal())
                        showWarning();
                    else
                        removeWarning();
                    break;
                case TRADE_PERIOD_OVER:
                    onOpenForDispute();
                    break;
            }
        }
    }
}
