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

package io.bitsquare.gui.main.portfolio.closed;

import io.bitsquare.common.viewfx.model.ActivatableWithDataModel;
import io.bitsquare.common.viewfx.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.Trade;

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
            Trade.LifeCycleState lifeCycleState = item.getTrade().lifeCycleStateProperty().get();
            if (lifeCycleState instanceof TakerTrade.TakerLifeCycleState) {
                switch ((TakerTrade.TakerLifeCycleState) lifeCycleState) {
                    case COMPLETED:
                        return "Completed";
                    case FAILED:
                        return "Failed";
                    case PENDING:
                        throw new RuntimeException("That must not happen. We got a pending state but we are in the closed trades list.");
                }
            }
            else if (lifeCycleState instanceof OffererTrade.OffererLifeCycleState) {
                switch ((OffererTrade.OffererLifeCycleState) lifeCycleState) {
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
            throw new RuntimeException("That must not happen. We got no defined state.");
        }
        else {
            return "";
        }
    }

}
