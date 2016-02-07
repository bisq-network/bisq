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

import io.bitsquare.arbitration.Dispute;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.components.paymentmethods.PaymentMethodForm;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.trade.Trade;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

public abstract class TradeStepView extends AnchorPane {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final PendingTradesViewModel model;
    private final Trade trade;
    protected final Preferences preferences;
    protected final GridPane gridPane;
    private final ChangeListener<String> txIdChangeListener;

    private Subscription errorMessageSubscription;
    private Subscription disputeStateSubscription;
    private Subscription tradePeriodStateSubscription;
    private Timer timer;
    protected int gridRow = 0;
    protected TitledGroupBg tradeInfoTitledGroupBg;
    private TextField timeLeftTextField;
    private ProgressBar timeLeftProgressBar;
    private GridPane notificationGridPane;
    private Label notificationLabel;
    private TitledGroupBg notificationTitledGroupBg;
    protected Button openDisputeButton;
    private TxIdTextField txIdTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TradeStepView(PendingTradesViewModel model) {
        this.model = model;
        preferences = model.dataModel.getPreferences();
        trade = model.getTrade();

        gridPane = addGridPane(this);

        AnchorPane.setLeftAnchor(this, 0d);
        AnchorPane.setRightAnchor(this, 0d);
        AnchorPane.setTopAnchor(this, -10d);
        AnchorPane.setBottomAnchor(this, 0d);

        txIdChangeListener = (ov, oldValue, newValue) -> txIdTextField.setup(newValue);

        addContent();
    }

    public void doActivate() {
        if (txIdTextField != null) {
            txIdTextField.setup(model.txIdProperty().get());

            model.txIdProperty().addListener(txIdChangeListener);
        }

        errorMessageSubscription = EasyBind.subscribe(trade.errorMessageProperty(), newValue -> {
            if (newValue != null) {
                showSupportFields();
            }
        });

        disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(), newValue -> {
            if (newValue != null) {
                updateDisputeState(newValue);
            }
        });

        timer = FxTimer.runPeriodically(Duration.ofSeconds(1), this::updateTimeLeft);

        tradePeriodStateSubscription = EasyBind.subscribe(trade.getTradePeriodStateProperty(), newValue -> {
            if (newValue != null) {
                updateTradePeriodState(newValue);
            }
        });
    }

    public void doDeactivate() {
        if (txIdTextField != null) {
            txIdTextField.cleanup();

            model.txIdProperty().removeListener(txIdChangeListener);
        }

        if (errorMessageSubscription != null)
            errorMessageSubscription.unsubscribe();

        if (disputeStateSubscription != null)
            disputeStateSubscription.unsubscribe();

        if (tradePeriodStateSubscription != null)
            tradePeriodStateSubscription.unsubscribe();

        if (openDisputeButton != null)
            openDisputeButton.setOnAction(null);

        if (timer != null)
            timer.stop();
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

        //TODO
        PaymentMethodForm.addAllowedPeriod(gridPane, ++gridRow, model.dataModel.getSellersPaymentAccountContractData(),
                model.getOpenDisputeTimeAsFormattedDate());

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
            String remainingTime = model.getRemainingTime();
            timeLeftProgressBar.setProgress(model.getRemainingTimeAsPercentage());
            if (remainingTime != null) {
                timeLeftTextField.setText(remainingTime);
                if (model.showWarning(model.getTrade()) || model.showDispute(model.getTrade())) {
                    timeLeftTextField.setStyle("-fx-text-fill: -bs-error-red");
                    timeLeftProgressBar.setStyle("-fx-accent: -bs-error-red;");
                }
            } else {
                timeLeftTextField.setText("Trade not completed in time (" + model.getOpenDisputeTimeAsFormattedDate() + ")");
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
    public void setNotificationFields(Tuple3<GridPane, TitledGroupBg, Label> notificationTuple) {
        this.notificationGridPane = notificationTuple.first;
        this.notificationTitledGroupBg = notificationTuple.second;
        this.notificationLabel = notificationTuple.third;
    }

    public void setOpenDisputeButton(Button openDisputeButton) {
        this.openDisputeButton = openDisputeButton;
    }

    private void showDisputeInfoLabel() {
        if (notificationGridPane != null) {
            notificationGridPane.setVisible(true);
            notificationGridPane.setManaged(true);
        }
    }

    private void showOpenDisputeButton() {
        if (openDisputeButton != null) {
            openDisputeButton.setVisible(true);
            openDisputeButton.setManaged(true);
            openDisputeButton.setOnAction(e -> {
                openDisputeButton.setDisable(true);
                onDisputeOpened();
                setDisputeState();
                model.dataModel.onOpenDispute();
            });
        }
    }

    protected void setWarningState() {
        if (notificationGridPane != null) {
            notificationTitledGroupBg.setText("Warning");
            //notificationGridPane.setId("trade-notification-warning");
        }
    }

    protected void setInformationState() {
        if (notificationGridPane != null) {
            notificationTitledGroupBg.setText("Notification");
            notificationTitledGroupBg.setId("titled-group-bg-warn");
            notificationTitledGroupBg.getLabel().setId("titled-group-bg-label-warn");
            //notificationLabel.setId("titled-group-bg-label-warn");
        }
    }

    protected void setDisputeState() {
        if (notificationGridPane != null) {
            notificationTitledGroupBg.setText("Dispute opened");
            //notificationGridPane.setId("trade-notification-dispute");
        }
    }

    protected void setSupportState() {
        if (notificationGridPane != null) {
            notificationTitledGroupBg.setText("Support ticket opened");
            //notificationGridPane.setId("trade-notification-support");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Support
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showSupportFields() {
        if (openDisputeButton != null) {
            openDisputeButton.setText("Request support");
            openDisputeButton.setId("open-support-button");
            openDisputeButton.setOnAction(e -> model.dataModel.onOpenSupportTicket());
        }
        new Popup().warning(trade.errorMessageProperty().getValue()
                + "\n\nPlease report the problem to your arbitrator.\n\n" +
                "He will forward teh information to the developers to investigate the problem.\n" +
                "After the problem has be analyzed you will get back all the funds if they are locked.\n" +
                "There will be no arbitration fee charged in case of a software bug.")
                .show();

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showWarning() {
        showDisputeInfoLabel();

        if (notificationLabel != null)
            notificationLabel.setText(getWarningText());
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

        if (notificationLabel != null)
            notificationLabel.setText(getOpenForDisputeText());
    }

    private void onDisputeOpened() {
        showDisputeInfoLabel();
        showOpenDisputeButton();
        applyOnDisputeOpened();


        if (openDisputeButton != null)
            openDisputeButton.setDisable(true);
    }

    protected String getOpenForDisputeText() {
        return "";
    }

    protected void applyOnDisputeOpened() {
    }

    private void updateDisputeState(Trade.DisputeState disputeState) {
        Optional<Dispute> ownDispute;
        switch (disputeState) {
            case NONE:
                break;
            case DISPUTE_REQUESTED:
                onDisputeOpened();
                ownDispute = model.dataModel.getDisputeManager().findOwnDispute(trade.getId());
                ownDispute.ifPresent(dispute -> {
                    String msg;
                    if (dispute.isSupportTicket()) {
                        setSupportState();
                        msg = "You opened already a support ticket.\n" +
                                "Please communicate in the support section with the arbitrator.";
                    } else {
                        setDisputeState();
                        msg = "You opened already a dispute.\n" +
                                "Please communicate in the support section with the arbitrator.";
                    }
                    if (notificationLabel != null)
                        notificationLabel.setText(msg);
                });

                break;
            case DISPUTE_STARTED_BY_PEER:
                onDisputeOpened();
                ownDispute = model.dataModel.getDisputeManager().findOwnDispute(trade.getId());
                ownDispute.ifPresent(dispute -> {
                    String msg;
                    if (dispute.isSupportTicket()) {
                        setSupportState();
                        msg = "Your trading peer opened a support ticket due technical problems.\n" +
                                "Please communicate in the support section with the arbitrator.";
                    } else {
                        setDisputeState();
                        msg = "Your trading peer opened a dispute.\n" +
                                "Please communicate in the support section with the arbitrator.";
                    }
                    if (notificationLabel != null)
                        notificationLabel.setText(msg);
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
                    showWarning();
                    break;
                case TRADE_PERIOD_OVER:
                    onOpenForDispute();
                    break;
            }
        }
    }
}
