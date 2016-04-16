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

import com.google.inject.Inject;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.OpenOffer;
import javafx.collections.ObservableList;

class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private final BSFormatter formatter;


    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel, BSFormatter formatter) {
        super(dataModel);

        this.formatter = formatter;
    }

    public ObservableList<ClosedTradableListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(ClosedTradableListItem item) {
        return item.getTradable().getShortId();
    }

    String getAmount(ClosedTradableListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return formatter.formatCoinWithCode(((Trade) item.getTradable()).getTradeAmount());
        else if (item != null && item.getTradable() instanceof OpenOffer)
            return "-";
        else
            return "";
    }

    String getPrice(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable instanceof Trade)
            return formatter.formatFiat(((Trade) tradable).getTradePrice());
        else
            return formatter.formatFiat(tradable.getOffer().getPrice());
    }

    String getVolume(ClosedTradableListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return formatter.formatFiatWithCode(((Trade) item.getTradable()).getTradeVolume());
        else if (item != null && item.getTradable() instanceof OpenOffer)
            return "-";
        else
            return "";
    }

    String getDirectionLabel(ClosedTradableListItem item) {
        return (item != null) ? formatter.getDirection(dataModel.getDirection(item.getTradable().getOffer())) : "";
    }

    String getDate(ClosedTradableListItem item) {
        return formatter.formatDateTime(item.getTradable().getDate());
    }

    String getState(ClosedTradableListItem item) {
        if (item != null) {
            if (item.getTradable() instanceof Trade) {
                Trade trade = (Trade) item.getTradable();

                // TODO 
               /* if (trade.isFailedState())
                    return "Failed";
                else*/
                if (trade.getState() == Trade.State.WITHDRAW_COMPLETED || trade.getState() == Trade.State.PAYOUT_BROAD_CASTED) {
                    return "Completed";
                } else if (trade.getDisputeState() == Trade.DisputeState.DISPUTE_CLOSED) {
                    return "Ticket closed";
                } else {
                    log.error("That must not happen. We got a pending state but we are in the closed trades list.");
                    return trade.getState().toString();
                }
            } else if (item.getTradable() instanceof OpenOffer) {
                OpenOffer.State state = ((OpenOffer) item.getTradable()).getState();
                log.trace("OpenOffer state {}", state);
                switch (state) {
                    case AVAILABLE:
                    case RESERVED:
                    case CLOSED:
                        log.error("Invalid state {}", state);
                        return state.toString();
                    case CANCELED:
                        return "Canceled";
                    default:
                        log.error("Unhandled state {}", state);
                        return state.toString();
                }
            }
        }
        return "";
    }
}
