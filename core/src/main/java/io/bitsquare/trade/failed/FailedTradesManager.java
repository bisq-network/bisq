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

package io.bitsquare.trade.failed;

import com.google.inject.Inject;
import io.bitsquare.btc.pricefeed.PriceFeed;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradableList;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.Offer;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.util.Optional;

public class FailedTradesManager {
    private static final Logger log = LoggerFactory.getLogger(FailedTradesManager.class);
    private final TradableList<Trade> failedTrades;
    private final KeyRing keyRing;

    @Inject
    public FailedTradesManager(KeyRing keyRing, PriceFeed priceFeed, @Named(Storage.DIR_KEY) File storageDir) {
        this.keyRing = keyRing;
        this.failedTrades = new TradableList<>(new Storage<>(storageDir), "FailedTrades");
        failedTrades.forEach(e -> e.getOffer().setPriceFeed(priceFeed));
    }

    public void add(Trade trade) {
        if (!failedTrades.contains(trade))
            failedTrades.add(trade);
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<Trade> getFailedTrades() {
        return failedTrades.getObservableList();
    }

    public Optional<Trade> getTradeById(String id) {
        return failedTrades.stream().filter(e -> e.getId().equals(id)).findFirst();
    }
}
