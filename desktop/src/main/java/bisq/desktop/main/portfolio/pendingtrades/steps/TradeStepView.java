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

import bisq.desktop.components.InfoTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.TradeSubView;
import bisq.desktop.util.Layout;

import bisq.core.arbitration.Dispute;
import bisq.core.locale.Res;
import bisq.core.trade.Trade;
import bisq.core.user.Preferences;

import bisq.common.Clock;
import bisq.common.util.Tuple3;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXProgressBar;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.value.ChangeListener;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.components.paymentmethods.PaymentMethodForm.addOpenTradeDuration;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelTxIdTextField;
import static com.google.common.base.Preconditions.checkNotNull;

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
    private final ChangeListener<String> errorMessageListener;
    protected Label infoLabel;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TradeStepView(PendingTradesViewModel model) {
        this.model = model;
        preferences = model.dataModel.preferences;
        trade = model.dataModel.getTrade();
        checkNotNull(trade, "Trade must not be null at TradeStepView");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);

        AnchorPane.setLeftAnchor(scrollPane, 10d);
        AnchorPane.setRightAnchor(scrollPane, 10d);
        AnchorPane.setTopAnchor(scrollPane, 10d);
        AnchorPane.setBottomAnchor(scrollPane, 0d);

        getChildren().add(scrollPane);

        gridPane = new GridPane();

        gridPane.setHgap(Layout.GRID_GAP);
        gridPane.setVgap(Layout.GRID_GAP);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHgrow(Priority.ALWAYS);

        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);

        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);

        scrollPane.setContent(gridPane);

        AnchorPane.setLeftAnchor(this, 0d);
        AnchorPane.setRightAnchor(this, 0d);
        AnchorPane.setTopAnchor(this, -10d);
        AnchorPane.setBottomAnchor(this, 0d);

        addContent();

        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                showSupportFields();
        };

        clockListener = new Clock.Listener() {
            @Override
            public void onSecondTick() {
            }

            @Override
            public void onMinuteTick() {
                updateTimeLeft();
            }
        };
    }

    public void activate() {
        if (txIdTextField != null) {
            if (txIdSubscription != null)
                txIdSubscription.unsubscribe();

            txIdSubscription = EasyBind.subscribe(model.dataModel.txId, id -> {
                if (!id.isEmpty())
                    txIdTextField.setup(id);
                else
                    txIdTextField.cleanup();
            });
        }
        trade.errorMessageProperty().addListener(errorMessageListener);

        disputeStateSubscription = EasyBind.subscribe(trade.disputeStateProperty(), newValue -> {
            if (newValue != null)
                updateDisputeState(newValue);
        });

        tradePeriodStateSubscription = EasyBind.subscribe(trade.tradePeriodStateProperty(), newValue -> {
            if (newValue != null)
                updateTradePeriodState(newValue);
        });

        model.clock.addListener(clockListener);

        if (infoLabel != null)
            infoLabel.setText(getInfoText());
    }

    public void deactivate() {
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
        tradeInfoTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 3,
                Res.get("portfolio.pending.tradeInformation"));
        GridPane.setColumnSpan(tradeInfoTitledGroupBg, 2);

        final Tuple3<Label, TxIdTextField, VBox> labelTxIdTextFieldVBoxTuple3 =
                addTopLabelTxIdTextField(gridPane, gridRow,
                        Res.get("shared.depositTransactionId"),
                        Layout.COMPACT_FIRST_ROW_DISTANCE);

        GridPane.setColumnSpan(labelTxIdTextFieldVBoxTuple3.third, 2);
        txIdTextField = labelTxIdTextFieldVBoxTuple3.second;

        String id = model.dataModel.txId.get();
        if (!id.isEmpty())
            txIdTextField.setup(id);
        else
            txIdTextField.cleanup();

        if (model.dataModel.getTrade() != null) {
            checkNotNull(model.dataModel.getTrade().getOffer(), "Offer must not be null in TradeStepView");
            InfoTextField infoTextField = addOpenTradeDuration(gridPane, ++gridRow,
                    model.dataModel.getTrade().getOffer());
            infoTextField.setContentForInfoPopOver(createInfoPopover());
        }

        final Tuple3<Label, TextField, VBox> labelTextFieldVBoxTuple3 = addCompactTopLabelTextField(gridPane, gridRow,
                1, Res.get("portfolio.pending.remainingTime"), "");

        timeLeftTextField = labelTextFieldVBoxTuple3.second;
        timeLeftTextField.setMinWidth(400);

        timeLeftProgressBar = new JFXProgressBar(0);
        timeLeftProgressBar.setOpacity(0.7);
        timeLeftProgressBar.setMinHeight(9);
        timeLeftProgressBar.setMaxHeight(9);
        timeLeftProgressBar.setMaxWidth(Double.MAX_VALUE);

        GridPane.setRowIndex(timeLeftProgressBar, ++gridRow);
        GridPane.setColumnSpan(timeLeftProgressBar, 2);
        GridPane.setFillWidth(timeLeftProgressBar, true);
        gridPane.getChildren().add(timeLeftProgressBar);

        updateTimeLeft();
    }

    protected void addInfoBlock() {
        final TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, getInfoBlockTitle(),
                Layout.COMPACT_GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last");
        GridPane.setColumnSpan(titledGroupBg, 2);

        infoLabel = addMultilineLabel(gridPane, gridRow, "", Layout.COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE);
//        infoLabel = addMultilineLabel(gridPane, gridRow, "", 0);
        GridPane.setColumnSpan(infoLabel, 2);
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
            if (!remainingTime.isEmpty()) {
                timeLeftTextField.setText(Res.get("portfolio.pending.remainingTimeDetail",
                        remainingTime, model.getDateForOpenDispute()));
                if (model.showWarning() || model.showDispute()) {
                    timeLeftTextField.getStyleClass().add("error-text");
                    timeLeftProgressBar.getStyleClass().add("error");
                }
            } else {
                timeLeftTextField.setText(Res.get("portfolio.pending.tradeNotCompleted",
                        model.getDateForOpenDispute()));
                timeLeftTextField.getStyleClass().add("error-text");
                timeLeftProgressBar.getStyleClass().add("error");
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
            notificationGroup.titledGroupBg.setText(Res.get("shared.warning"));
        }
    }

    protected void setInformationHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText(Res.get("portfolio.pending.notification"));
        }
    }

    protected void setOpenDisputeHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText(Res.get("portfolio.pending.openDispute"));
        }
    }

    protected void setDisputeOpenedHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText(Res.get("portfolio.pending.disputeOpened"));
        }
    }

    protected void setRequestSupportHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText(Res.get("portfolio.pending.openSupport"));
        }
    }

    protected void setSupportOpenedHeadline() {
        if (notificationGroup != null) {
            notificationGroup.titledGroupBg.setText(Res.get("portfolio.pending.supportTicketOpened"));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Support
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showSupportFields() {
        if (notificationGroup != null) {
            notificationGroup.button.updateText(Res.get("portfolio.pending.requestSupport"));
            notificationGroup.button.setId("open-support-button");
            notificationGroup.button.setOnAction(e -> model.dataModel.onOpenSupportTicket());
        }
        new Popup<>().warning(trade.errorMessageProperty().getValue()
                + "\n\n" + Res.get("portfolio.pending.error.requestSupport"))
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
            case NO_DISPUTE:
                break;
            case DISPUTE_REQUESTED:
                onDisputeOpened();
                ownDispute = model.dataModel.disputeManager.findOwnDispute(trade.getId());
                ownDispute.ifPresent(dispute -> {
                    String msg;
                    if (dispute.isSupportTicket()) {
                        setSupportOpenedHeadline();
                        msg = Res.get("portfolio.pending.supportTicketOpenedMyUser", Res.get("portfolio.pending.communicateWithArbitrator"));
                    } else {
                        setDisputeOpenedHeadline();
                        msg = Res.get("portfolio.pending.disputeOpenedMyUser", Res.get("portfolio.pending.communicateWithArbitrator"));
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
                        msg = Res.get("portfolio.pending.supportTicketOpenedByPeer", Res.get("portfolio.pending.communicateWithArbitrator"));
                    } else {
                        setDisputeOpenedHeadline();
                        msg = Res.get("portfolio.pending.disputeOpenedByPeer", Res.get("portfolio.pending.communicateWithArbitrator"));
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
                case FIRST_HALF:
                    break;
                case SECOND_HALF:
                    if (!trade.isFiatReceived())
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeDurationLimitInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GridPane createInfoPopover() {
        GridPane infoGridPane = new GridPane();
        int rowIndex = 0;
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(10);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));
        Label label = addMultilineLabel(infoGridPane, rowIndex++, Res.get("portfolio.pending.tradePeriodInfo"));
        label.setMaxWidth(450);

        HBox warningBox = new HBox();
        warningBox.setMinHeight(30);
        warningBox.setPadding(new Insets(5));
        warningBox.getStyleClass().add("warning-box");
        GridPane.setRowIndex(warningBox, rowIndex);
        GridPane.setColumnSpan(warningBox, 2);

        Label warningIcon = new Label();
        AwesomeDude.setIcon(warningIcon, AwesomeIcon.WARNING_SIGN);
        warningIcon.getStyleClass().add("warning");

        Label warning = new Label(Res.get("portfolio.pending.tradePeriodWarning"));
        warning.setWrapText(true);
        warning.setMaxWidth(410);

        warningBox.getChildren().addAll(warningIcon, warning);
        infoGridPane.getChildren().add(warningBox);

        return infoGridPane;
    }
}
