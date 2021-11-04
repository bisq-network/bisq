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

package bisq.core.trade.model;

import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;

import bisq.common.proto.persistable.PersistablePayload;

import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.Optional;

public interface Tradable extends PersistablePayload {
    Offer getOffer();

    Date getDate();

    String getId();

    String getShortId();

    default Optional<TradeModel> asTradeModel() {
        if (this instanceof TradeModel) {
            return Optional.of(((TradeModel) this));
        } else {
            return Optional.empty();
        }
    }

    default Optional<Volume> getOptionalVolume() {
        return asTradeModel().map(TradeModel::getVolume).or(() -> Optional.ofNullable(getOffer().getVolume()));
    }

    default Optional<Price> getOptionalPrice() {
        return asTradeModel().map(TradeModel::getPrice).or(() -> Optional.ofNullable(getOffer().getPrice()));
    }

    default Optional<Coin> getOptionalAmount() {
        return asTradeModel().map(TradeModel::getAmount);
    }

    default Optional<Long> getOptionalAmountAsLong() {
        return asTradeModel().map(TradeModel::getAmountAsLong);
    }

    default Optional<Coin> getOptionalTxFee() {
        return asTradeModel().map(TradeModel::getTxFee);
    }

    default Optional<Coin> getOptionalTakerFee() {
        return asTradeModel().map(TradeModel::getTakerFee);
    }

    default Optional<Coin> getOptionalMakerFee() {
        return asTradeModel().map(TradeModel::getMakerFee).or(() -> Optional.ofNullable(getOffer().getMakerFee()));
    }
}
