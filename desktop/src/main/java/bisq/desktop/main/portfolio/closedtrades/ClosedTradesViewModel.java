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

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.util.DisplayUtils;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private final CoinFormatter formatter;
    final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {
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
            return formatter.formatCoin(((Trade) item.getTradable()).getTradeAmount());
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
            return FormattingUtils.formatPrice(((Trade) tradable).getTradePrice());
        else
            return FormattingUtils.formatPrice(tradable.getOffer().getPrice());
    }

    String getVolume(ClosedTradableListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return DisplayUtils.formatVolumeWithCode(((Trade) item.getTradable()).getTradeVolume());
        else if (item != null && item.getTradable() instanceof OpenOffer)
            return "-";
        else
            return "";
    }

    String getTxFee(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable instanceof Trade)
            return formatter.formatCoin(((Trade) tradable).getTxFee());
        else
            return formatter.formatCoin(tradable.getOffer().getTxFee());
    }

    String getMakerFee(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable instanceof Trade)
            return formatter.formatCoin(((Trade) tradable).getTakerFee());
        else
            return formatter.formatCoin(tradable.getOffer().getMakerFee());
    }

    String getBuyerSecurityDeposit(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable.getOffer() != null)
            return formatter.formatCoin(tradable.getOffer().getBuyerSecurityDeposit());
        else
            return "";
    }

    String getSellerSecurityDeposit(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable.getOffer() != null)
            return formatter.formatCoin(tradable.getOffer().getSellerSecurityDeposit());
        else
            return "";
    }

    String getDirectionLabel(ClosedTradableListItem item) {
        return (item != null) ? DisplayUtils.getDirectionWithCode(dataModel.getDirection(item.getTradable().getOffer()), item.getTradable().getOffer().getCurrencyCode()) : "";
    }

    String getDate(ClosedTradableListItem item) {
        return DisplayUtils.formatDateTime(item.getTradable().getDate());
    }

    String getMarketLabel(ClosedTradableListItem item) {
        if ((item == null))
            return "";

        return CurrencyUtil.getCurrencyPair(item.getTradable().getOffer().getCurrencyCode());
    }

    String getState(ClosedTradableListItem item) {
        if (item != null) {
            if (item.getTradable() instanceof Trade) {
                Trade trade = (Trade) item.getTradable();

                if (trade.isWithdrawn() || trade.isPayoutPublished()) {
                    return Res.get("portfolio.closed.completed");
                } else if (trade.getDisputeState() == Trade.DisputeState.DISPUTE_CLOSED) {
                    return Res.get("portfolio.closed.ticketClosed");
                } else if (trade.getDisputeState() == Trade.DisputeState.MEDIATION_CLOSED) {
                    return Res.get("portfolio.closed.mediationTicketClosed");
                } else if (trade.getDisputeState() == Trade.DisputeState.REFUND_REQUEST_CLOSED) {
                    return Res.get("portfolio.closed.ticketClosed");
                } else {
                    log.error("That must not happen. We got a pending state but we are in the closed trades list. state={}", trade.getState().toString());
                    return Res.get("shared.na");
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
                    case DEACTIVATED:
                        log.error("Invalid state {}", state);
                        return state.toString();
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
