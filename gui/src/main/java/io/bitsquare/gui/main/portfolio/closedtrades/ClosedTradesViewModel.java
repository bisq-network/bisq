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

import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeState;
import io.bitsquare.trade.offer.OpenOffer;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(ClosedTradesViewModel.class);

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
        return item.getTradable().getId();
    }

    String getAmount(ClosedTradesListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return formatter.formatCoinWithCode(((Trade) item.getTradable()).getTradeAmount());
        else
            return "";
    }

    String getPrice(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatFiat(item.getTradable().getOffer().getPrice()) : "";
    }

    String getVolume(ClosedTradesListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return formatter.formatFiatWithCode(((Trade) item.getTradable()).getTradeVolume());
        else
            return "";
    }

    String getDirectionLabel(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatDirection(dataModel.getDirection(item.getTradable().getOffer())) : "";
    }

    String getDate(ClosedTradesListItem item) {
        return formatter.formatDateTime(item.getTradable().getDate());
    }

    String getState(ClosedTradesListItem item) {
        if (item != null) {
            if (item.getTradable() instanceof Trade) {
                Trade trade = (Trade) item.getTradable();
                TradeState tradeState = trade.tradeStateProperty().get();
                if (trade instanceof BuyerTrade) {
                    if (tradeState == TradeState.BuyerState.FAILED) {
                        return "Failed";
                    }
                    else if (tradeState == TradeState.BuyerState.WITHDRAW_COMPLETED) {
                        return "Completed";
                    }
                    else {
                        log.error("That must not happen. We got a pending state but we are in the closed trades list.");
                    }
                }
                else if (trade instanceof SellerTrade) {
                    if (tradeState == TradeState.SellerState.FAILED) {
                        return "Failed";
                    }
                    else if (tradeState == TradeState.SellerState.WITHDRAW_COMPLETED) {
                        return "Completed";
                    }
                    else {
                        log.error("That must not happen. We got a pending state but we are in the closed trades list.");
                    }
                }
            }
            else if (item.getTradable() instanceof OpenOffer) {
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
                 /*   case FAILED:
                        return "Failed";*/
                    default:
                        log.error("Unhandled state {}", state);
                        return state.toString();
                }
            }
        }
        return "";
    }
}
