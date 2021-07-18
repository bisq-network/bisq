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

package bisq.desktop.main.portfolio.failedtrades;

import bisq.desktop.common.model.ActivatableDataModel;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.failed.FailedTradesManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.stream.Collectors;

class FailedTradesDataModel extends ActivatableDataModel {

    private final FailedTradesManager failedTradesManager;
    private final TradeManager tradeManager;
    private final P2PService p2PService;
    private final KeyRing keyRing;

    private final ObservableList<FailedTradesListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Trade> tradesListChangeListener;

    @Inject
    public FailedTradesDataModel(FailedTradesManager failedTradesManager,
                                 TradeManager tradeManager,
                                 P2PService p2PService,
                                 KeyRing keyRing) {
        this.failedTradesManager = failedTradesManager;
        this.tradeManager = tradeManager;
        this.p2PService = p2PService;
        this.keyRing = keyRing;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    protected void activate() {
        applyList();
        failedTradesManager.getObservableList().addListener(tradesListChangeListener);
    }

    @Override
    protected void deactivate() {
        failedTradesManager.getObservableList().removeListener(tradesListChangeListener);
    }

    public ObservableList<FailedTradesListItem> getList() {
        return list;
    }

    public OfferPayload.Direction getDirection(Offer offer) {
        return failedTradesManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(failedTradesManager.getObservableList().stream().map(FailedTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));
    }

    public boolean onMoveTradeToPendingTrades(Trade trade) {
        if (!isTradeWithSameCurrentNodeAddress(trade)) {
            return false;
        }

        failedTradesManager.removeTrade(trade);
        tradeManager.addFailedTradeToPendingTrades(trade);
        return true;
    }

    private boolean isTradeWithSameCurrentNodeAddress(Trade trade) {
        NodeAddress tradeNodeAddress = trade.getContract().getMyNodeAddress(keyRing.getPubKeyRing());
        return Objects.equals(p2PService.getNetworkNode().getNodeAddress(), tradeNodeAddress);
    }

    public void unfailTrade(Trade trade) {
        failedTradesManager.unFailTrade(trade);
    }

    public String checkUnfail(Trade trade) {
        return failedTradesManager.checkUnFail(trade);
    }
}
