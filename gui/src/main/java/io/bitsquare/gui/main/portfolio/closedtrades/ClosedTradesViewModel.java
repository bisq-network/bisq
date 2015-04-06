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

package io.bitsquare.gui.main.portfolio.closedtrades;

import io.bitsquare.common.model.ActivatableWithDataModel;
import io.bitsquare.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.states.OffererTradeState;
import io.bitsquare.trade.states.TakerTradeState;
import io.bitsquare.trade.states.TradeState;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {

    private final BSFormatter formatter;


    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel, BSFormatter formatter) {
        super(dataModel);

        this.formatter = formatter;
    }

    public ObservableList<ClosedTradesListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(ClosedTradesListItem item) {
        return item.getTrade().getId();
    }

    String getAmount(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatCoinWithCode(item.getTrade().getTradeAmount()) : "";
    }

    String getPrice(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatFiat(item.getTrade().getOffer().getPrice()) : "";
    }

    String getVolume(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatFiatWithCode(item.getTrade().getTradeVolume()) : "";
    }

    String getDirectionLabel(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatDirection(dataModel.getDirection(item.getTrade().getOffer())) : "";
    }

    String getDate(ClosedTradesListItem item) {
        return formatter.formatDateTime(item.getTrade().getDate());
    }

    String getState(ClosedTradesListItem item) {
        if (item != null && item.getTrade() != null) {
            TradeState.LifeCycleState lifeCycleState = item.getTrade().lifeCycleStateProperty().get();
            if (lifeCycleState instanceof TakerTradeState.LifeCycleState) {
                switch ((TakerTradeState.LifeCycleState) lifeCycleState) {
                    case COMPLETED:
                        return "Completed";
                    case FAILED:
                        return "Failed";
                    case PENDING:
                        throw new RuntimeException("That must not happen. We got a pending state but we are in the closed trades list.");
                }
            }
            else if (lifeCycleState instanceof OffererTradeState.LifeCycleState) {
                switch ((OffererTradeState.LifeCycleState) lifeCycleState) {
                    case OFFER_CANCELED:
                        return "Canceled";
                    case COMPLETED:
                        return "Completed";
                    case FAILED:
                        return "Failed";
                    case OFFER_OPEN:
                    case PENDING:
                        throw new RuntimeException("That must not happen. We got a pending state but we are in the closed trades list.");
                }
            }
        }
        return "";
    }
}
