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

package io.bisq.gui.main.portfolio.closedtrades;

import com.google.inject.Inject;
import io.bisq.common.locale.Res;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import io.bisq.gui.common.model.ActivatableWithDataModel;
import io.bisq.gui.common.model.ViewModel;
import io.bisq.gui.util.BSFormatter;
import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private final BSFormatter formatter;
    final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel, 
                                 AccountAgeWitnessService accountAgeWitnessService, 
                                 BSFormatter formatter) {
        super(dataModel);
        this.accountAgeWitnessService = accountAgeWitnessService;
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
            return formatter.formatPrice(((Trade) tradable).getTradePrice());
        else
            return formatter.formatPrice(tradable.getOffer().getPrice());
    }

    String getVolume(ClosedTradableListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return formatter.formatVolumeWithCode(((Trade) item.getTradable()).getTradeVolume());
        else if (item != null && item.getTradable() instanceof OpenOffer)
            return "-";
        else
            return "";
    }

    String getDirectionLabel(ClosedTradableListItem item) {
        return (item != null) ? formatter.getDirectionWithCode(dataModel.getDirection(item.getTradable().getOffer()), item.getTradable().getOffer().getCurrencyCode()) : "";
    }

    String getDate(ClosedTradableListItem item) {
        return formatter.formatDateTime(item.getTradable().getDate());
    }

    String getMarketLabel(ClosedTradableListItem item) {
        if ((item == null))
            return "";

        return formatter.getCurrencyPair(item.getTradable().getOffer().getCurrencyCode());
    }

    String getState(ClosedTradableListItem item) {
        if (item != null) {
            if (item.getTradable() instanceof Trade) {
                Trade trade = (Trade) item.getTradable();

                if (trade.isWithdrawn() || trade.isPayoutPublished()) {
                    return Res.get("portfolio.closed.completed");
                } else if (trade.getDisputeState() == Trade.DisputeState.DISPUTE_CLOSED) {
                    return Res.get("portfolio.closed.ticketClosed");
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
                        return Res.get("portfolio.closed.canceled");
                    default:
                        log.error("Unhandled state {}", state);
                        return state.toString();
                }
            }
        }
        return "";
    }

    int getNumPastTrades(Tradable tradable) {
        //noinspection ConstantConditions
        return dataModel.closedTradableManager.getClosedTradables().stream()
                .filter(e -> e instanceof Trade &&
                        tradable instanceof Trade &&
                        ((Trade) e).getTradingPeerNodeAddress() != null &&
                        ((Trade) tradable).getTradingPeerNodeAddress() != null &&
                        ((Trade) e).getTradingPeerNodeAddress() != null &&
                        ((Trade) tradable).getTradingPeerNodeAddress() != null &&
                        ((Trade) e).getTradingPeerNodeAddress().getFullAddress().equals(((Trade) tradable).getTradingPeerNodeAddress().getFullAddress()))
                .collect(Collectors.toSet())
                .size();
    }
}
