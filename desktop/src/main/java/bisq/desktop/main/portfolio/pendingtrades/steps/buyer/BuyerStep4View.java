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
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.notifications.Notification;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.TradeFeedbackWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.closedtrades.ClosedTradesView;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.Layout;

import bisq.core.btc.model.AddressEntry;
import bisq.core.locale.Res;
import bisq.core.trade.txproof.AssetTxProofResult;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import com.jfoenix.controls.JFXBadge;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;

public class BuyerStep4View extends TradeStepView {

    private Button closeButton;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep4View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    public void activate() {
        super.activate();
        // Don't display any trade step info when trade is complete
        hideTradeStepInfo();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addContent() {
        gridPane.getColumnConstraints().get(1).setHgrow(Priority.SOMETIMES);

        TitledGroupBg completedTradeLabel = new TitledGroupBg();
        if (trade.getDisputeState().isMediated()) {
            completedTradeLabel.setText(Res.get("portfolio.pending.step5_buyer.groupTitle.mediated"));
        } else if (trade.getDisputeState().isArbitrated()) {
            completedTradeLabel.setText(Res.get("portfolio.pending.step5_buyer.groupTitle.arbitrated"));
        } else {
            completedTradeLabel.setText(Res.get("portfolio.pending.step5_buyer.groupTitle"));
        }
        JFXBadge autoConfBadge = new JFXBadge(new Label(""), Pos.BASELINE_RIGHT);
        autoConfBadge.setText(Res.get("portfolio.pending.autoConf"));
        autoConfBadge.getStyleClass().add("auto-conf");

        HBox hBox2 = new HBox(1, completedTradeLabel, autoConfBadge);
        GridPane.setMargin(hBox2, new Insets(18, -10, -12, -10));
        gridPane.getChildren().add(hBox2);
        GridPane.setRowSpan(hBox2, 5);
        autoConfBadge.setVisible(AssetTxProofResult.COMPLETED == trade.getAssetTxProofResult());

        if (trade.getDisputeState().isNotDisputed()) {
            addCompactTopLabelTextField(gridPane, gridRow, getBtcTradeAmountLabel(), model.getTradeVolume(), Layout.TWICE_FIRST_ROW_DISTANCE);
            addCompactTopLabelTextField(gridPane, ++gridRow, getFiatTradeAmountLabel(), model.getFiatVolume());
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.refunded"), model.getSecurityDeposit());
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step5_buyer.tradeFee"), model.getTradeFee());
            final String miningFee = model.dataModel.isMaker() ?
                    Res.get("portfolio.pending.step5_buyer.makersMiningFee") :
                    Res.get("portfolio.pending.step5_buyer.takersMiningFee");
            addCompactTopLabelTextField(gridPane, ++gridRow, miningFee, model.getTxFee());
        }

        closeButton = new AutoTooltipButton(Res.get("shared.close"));
        closeButton.setDefaultButton(true);
        closeButton.getStyleClass().add("action-button");
        GridPane.setRowIndex(closeButton, ++gridRow);
        GridPane.setMargin(closeButton, new Insets(Layout.GROUP_DISTANCE, 10, 0, 0));
        gridPane.getChildren().add(closeButton);

        closeButton.setOnAction(e -> {
            handleTradeCompleted();
            model.dataModel.tradeManager.onTradeCompleted(trade);
        });

        String key = "tradeCompleted" + trade.getId();
        if (!DevEnv.isDevMode() && DontShowAgainLookup.showAgain(key)) {
            DontShowAgainLookup.dontShowAgain(key, true);
            new Notification().headLine(Res.get("notification.tradeCompleted.headline"))
                    .notification(Res.get("notification.tradeCompleted.msg"))
                    .autoClose()
                    .show();
        }
    }

    private void handleTradeCompleted() {
        closeButton.setDisable(true);
        model.dataModel.btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);

        openTradeFeedbackWindow();
    }

    private void openTradeFeedbackWindow() {
        String key = "feedbackPopupAfterTrade";
        if (!DevEnv.isDevMode() && preferences.showAgain(key)) {
            UserThread.runAfter(() -> new TradeFeedbackWindow()
                    .dontShowAgainId(key)
                    .onAction(this::showNavigateToClosedTradesViewPopup)
                    .show(), 500, TimeUnit.MILLISECONDS);
        } else {
            showNavigateToClosedTradesViewPopup();
        }
    }

    private void showNavigateToClosedTradesViewPopup() {
        if (!DevEnv.isDevMode()) {
            UserThread.runAfter(() -> new Popup().headLine(Res.get("portfolio.pending.step5_buyer.tradeCompleted.headline"))
                    .feedback(Res.get("portfolio.pending.step5_buyer.tradeCompleted.msg"))
                    .actionButtonTextWithGoTo("navigation.portfolio.closedTrades")
                    .onAction(() -> model.dataModel.navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class))
                    .dontShowAgainId("tradeCompleteWithdrawCompletedInfo")
                    .show(), 500, TimeUnit.MILLISECONDS);
        }
    }

    protected String getBtcTradeAmountLabel() {
        return Res.get("portfolio.pending.step5_buyer.bought");
    }

    protected String getFiatTradeAmountLabel() {
        return Res.get("portfolio.pending.step5_buyer.paid");
    }
}
