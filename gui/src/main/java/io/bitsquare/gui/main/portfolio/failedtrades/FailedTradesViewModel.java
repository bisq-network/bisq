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

import com.google.inject.Inject;
import io.bitsquare.gui.common.model.ActivatableWithDataModel;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Trade;
import javafx.collections.ObservableList;

class FailedTradesViewModel extends ActivatableWithDataModel<FailedTradesDataModel> implements ViewModel {
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
        return item.getTrade().getShortId();
    }

    String getAmount(FailedTradesListItem item) {
        if (item != null && item.getTrade() != null)
            return formatter.formatCoinWithCode(item.getTrade().getTradeAmount());
        else
            return "";
    }

    String getPrice(FailedTradesListItem item) {
        return (item != null) ? formatter.formatPrice(item.getTrade().getTradePrice()) : "";
    }

    String getVolume(FailedTradesListItem item) {
        if (item != null && item.getTrade() != null)
            return formatter.formatVolumeWithCode(item.getTrade().getTradeVolume());
        else
            return "";
    }

    String getDirectionLabel(FailedTradesListItem item) {
        return (item != null) ? formatter.getDirectionWithCode(dataModel.getDirection(item.getTrade().getOffer()), item.getTrade().getOffer().getCurrencyCode()) : "";
    }

    String getMarketLabel(FailedTradesListItem item) {
        if ((item == null))
            return "";

        return formatter.getCurrencyPair(item.getTrade().getOffer().getCurrencyCode());
    }

    String getDate(FailedTradesListItem item) {
        return formatter.formatDateTime(item.getTrade().getDate());
    }

    String getState(FailedTradesListItem item) {
        if (item != null) {
            Trade trade = item.getTrade();
            //TODO
            //if (trade.isFailedState())
            return "Failed";
           /* else {
                log.error("Wrong state " + trade.getTradeState());
                return trade.getTradeState().toString();
            }*/
        }
        return "";
    }
}
