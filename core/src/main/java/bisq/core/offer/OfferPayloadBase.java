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

package bisq.core.offer;

import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;

import bisq.common.crypto.PubKeyRing;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public abstract class OfferPayloadBase implements ProtectedStoragePayload, ExpirablePayload, RequiresOwnerIsOnlinePayload {

    public static final long TTL = TimeUnit.MINUTES.toMillis(9);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Direction {
        BUY,
        SELL;
    }

    abstract public long getAmount();

    abstract public long getMinAmount();

    // For fiat offer the baseCurrencyCode is BTC and the counterCurrencyCode is the fiat currency
    // For altcoin offers it is the opposite. baseCurrencyCode is the altcoin and the counterCurrencyCode is BTC.
    abstract public String getBaseCurrencyCode();

    abstract public String getCounterCurrencyCode();

    abstract public long getDate();

    abstract public Direction getDirection();

    @Nullable
    abstract public String getHashOfChallenge();

    abstract public String getId();

    abstract public boolean isCurrencyForMakerFeeBtc();

    // Reserved for possible future use to support private trades where the taker needs to have an accessKey
    abstract public boolean isPrivateOffer();

    abstract public long getMakerFee();

    abstract public String getMakerPaymentAccountId();

    abstract public long getMaxTradeLimit();

    abstract public long getMaxTradePeriod();

    abstract public String getPaymentMethodId();

    abstract public long getPrice();

    abstract public int getProtocolVersion();

    abstract public PubKeyRing getPubKeyRing();

    abstract public String getVersionNr();

    // In the offer we support base and counter currency
    // Fiat offers have base currency BTC and counterCurrency Fiat
    // Altcoins have base currency Altcoin and counterCurrency BTC
    // The rest of the app does not support yet that concept of base currency and counter currencies
    // so we map here for convenience
    public String getCurrencyCode() {
        return getBaseCurrencyCode().equals("BTC") ? getCounterCurrencyCode() : getBaseCurrencyCode();
    }

    abstract public byte[] getHash();
}
