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

import bisq.desktop.components.paymentmethods.F2FForm;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.steps.TradeStepView;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.payment.payload.F2FAccountPayload;
import bisq.core.trade.Trade;

import bisq.common.Timer;
import bisq.common.UserThread;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addMultilineLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static com.google.common.base.Preconditions.checkNotNull;

public class SellerStep2View extends TradeStepView {

    private GridPane refreshButtonPane;
    private Timer timer;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerStep2View(PendingTradesViewModel model) {
        super(model);
    }

    @Override
    protected void initialize() {
        addTradeInfoBlock();
        addInfoBlock();
        checkNotNull(model.dataModel.getTrade(), "No trade found");
        checkNotNull(model.dataModel.getTrade().getOffer(), "No offer found");
        if (model.dataModel.getSellersPaymentAccountPayload() instanceof F2FAccountPayload) {
            addTitledGroupBg(gridPane, ++gridRow, 4,
                    Res.get("portfolio.pending.step2_seller.f2fInfo.headline"), Layout.COMPACT_GROUP_DISTANCE);
            gridRow = F2FForm.addFormForBuyer(gridPane, --gridRow, model.dataModel.getSellersPaymentAccountPayload(),
                    model.dataModel.getTrade().getOffer(), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);
        }

        addRefreshBlock();
    }

    private void addRefreshBlock() {
        refreshButtonPane = new GridPane();
        addTitledGroupBg(refreshButtonPane, 0, 1,
                Res.get("portfolio.pending.step2_seller.refresh"), Layout.COMPACT_GROUP_DISTANCE);
        addMultilineLabel(refreshButtonPane, 1, Res.get("portfolio.pending.step2_seller.refreshInfo"),
                Layout.COMPACT_FIRST_ROW_DISTANCE);
        Button refreshButton = addButtonAfterGroup(refreshButtonPane, 2, Res.get("portfolio.pending.step2_seller.refresh"));
        refreshButton.setOnAction(event -> onRefreshButton());

        GridPane.setRowIndex(refreshButtonPane, ++gridRow);
        GridPane.setColumnIndex(refreshButtonPane, 0);
        GridPane.setColumnSpan(refreshButtonPane, 2);
        gridPane.getChildren().add(refreshButtonPane);
    }


    @Override
    public void activate() {
        super.activate();
        activateRefreshButton();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        deActivateRefreshButtonTimer();
    }

    private void activateRefreshButton() {
        checkNotNull(model.dataModel.getTrade(), "No trade found");

        Trade trade = model.dataModel.getTrade();
        var timeToNextRefresh =
                trade.getLastRefreshRequestDate() + trade.getRefreshInterval() - new Date().getTime();
        if (timeToNextRefresh <= 0) {
            refreshButtonPane.setVisible(true);
        } else {
            refreshButtonPane.setVisible(false);
            timer = UserThread.runAfter(this::activateRefreshButton, timeToNextRefresh, TimeUnit.MILLISECONDS);
        }
    }

    private void deActivateRefreshButtonTimer() {
        if (timer != null) {
            timer.stop();
        }
    }

    private void onRefreshButton() {
        model.dataModel.refreshTradeState();
        activateRefreshButton();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        return Res.get("portfolio.pending.step2_seller.waitPayment.headline");
    }

    @Override
    protected String getInfoText() {
        return Res.get("portfolio.pending.step2_seller.waitPayment.msg", getCurrencyCode(trade));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getFirstHalfOverWarnText() {
        return Res.get("portfolio.pending.step2_seller.warn",
                getCurrencyCode(trade),
                model.getDateForOpenDispute());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getPeriodOverWarnText() {
        return Res.get("portfolio.pending.step2_seller.openForDispute");
    }

    @Override
    protected void applyOnDisputeOpened() {
    }
}


