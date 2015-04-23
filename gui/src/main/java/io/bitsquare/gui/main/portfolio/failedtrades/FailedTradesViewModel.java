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

package io.bitsquare.gui.main.portfolio.failedtrades;

import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.BuyerTrade;
import io.bitsquare.trade.SellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeState;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FailedTradesViewModel extends ActivatableWithDataModel<FailedTradesDataModel> implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(FailedTradesViewModel.class);

    private final BSFormatter formatter;


    @Inject
    public FailedTradesViewModel(FailedTradesDataModel dataModel, BSFormatter formatter) {
        super(dataModel);

        this.formatter = formatter;
    }

    public ObservableList<FailedTradesListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(FailedTradesListItem item) {
        return item.getTrade().getId();
    }

    String getAmount(FailedTradesListItem item) {
        if (item != null && item.getTrade() instanceof Trade)
            return formatter.formatCoinWithCode(((Trade) item.getTrade()).getTradeAmount());
        else
            return "";
    }

    String getPrice(FailedTradesListItem item) {
        return (item != null) ? formatter.formatFiat(item.getTrade().getOffer().getPrice()) : "";
    }

    String getVolume(FailedTradesListItem item) {
        if (item != null && item.getTrade() instanceof Trade)
            return formatter.formatFiatWithCode(((Trade) item.getTrade()).getTradeVolume());
        else
            return "";
    }

    String getDirectionLabel(FailedTradesListItem item) {
        return (item != null) ? formatter.formatDirection(dataModel.getDirection(item.getTrade().getOffer())) : "";
    }

    String getDate(FailedTradesListItem item) {
        return formatter.formatDateTime(item.getTrade().getDate());
    }

    String getState(FailedTradesListItem item) {
        if (item != null) {
            Trade trade = item.getTrade();
            TradeState tradeState = trade.tradeStateProperty().get();
            if (trade instanceof BuyerTrade) {
                if (tradeState == TradeState.BuyerState.FAILED) {
                    return "Failed";
                }
                else {
                    log.error("Wrong state " + tradeState);
                    return tradeState.toString();
                }
            }
            else if (trade instanceof SellerTrade) {
                if (tradeState == TradeState.SellerState.FAILED) {
                    return "Failed";
                }
                else {
                    log.error("Wrong state " + tradeState);
                    return tradeState.toString();
                }
            }
        }
        return "";
    }
}
