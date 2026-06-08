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

import bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;

import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalMapEntryIterator;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.util.Hex;
import bisq.common.util.JsonExclude;

import java.security.PublicKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(exclude = {"hash"})
@Getter
public abstract class OfferPayloadBase implements ProtectedStoragePayload, ExpirablePayload, RequiresOwnerIsOnlinePayload,
        Canonical {
    public static final long TTL = TimeUnit.MINUTES.toMillis(9);

    protected final String id;
    protected final long date;
    // For fiat offer the baseCurrencyCode is BTC and the counterCurrencyCode is the fiat currency
    // For altcoin offers it is the opposite. baseCurrencyCode is the altcoin and the counterCurrencyCode is BTC.
    protected final String baseCurrencyCode;
    protected final String counterCurrencyCode;
    // price if fixed price is used (usePercentageBasedPrice = false), otherwise 0
    protected final long price;
    protected final long amount;
    protected final long minAmount;
    protected final String paymentMethodId;
    protected final String makerPaymentAccountId;
    protected final NodeAddress ownerNodeAddress;
    protected final OfferDirection direction;
    protected final String versionNr;
    protected final int protocolVersion;
    @JsonExclude
    protected final PubKeyRing pubKeyRing;
    // cache
    protected transient byte[] hash;
    @Nullable
    protected final OfferPayloadExtraDataMap offerPayloadExtraDataMap;

    public OfferPayloadBase(String id,
                            long date,
                            NodeAddress ownerNodeAddress,
                            PubKeyRing pubKeyRing,
                            String baseCurrencyCode,
                            String counterCurrencyCode,
                            OfferDirection direction,
                            long price,
                            long amount,
                            long minAmount,
                            String paymentMethodId,
                            String makerPaymentAccountId,
                            @Nullable OfferPayloadExtraDataMap offerPayloadExtraDataMap,
                            String versionNr,
                            int protocolVersion) {
        this.id = id;
        this.date = date;
        this.ownerNodeAddress = ownerNodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.baseCurrencyCode = baseCurrencyCode;
        this.counterCurrencyCode = counterCurrencyCode;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.minAmount = minAmount;
        this.paymentMethodId = paymentMethodId;
        this.makerPaymentAccountId = makerPaymentAccountId;
        this.offerPayloadExtraDataMap = offerPayloadExtraDataMap;
        this.versionNr = versionNr;
        this.protocolVersion = protocolVersion;
    }

    public byte[] getHash() {
        if (this.hash == null) {
            this.hash = Hash.getSha256Hash(encodeCanonical());
        }
        return this.hash;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    // In the offer we support base and counter currency
    // Fiat offers have base currency BTC and counterCurrency Fiat
    // Altcoins have base currency Altcoin and counterCurrency BTC
    // The rest of the app does not support yet that concept of base currency and counter currencies
    // so we map here for convenience
    public String getCurrencyCode() {
        return getBaseCurrencyCode().equals("BTC") ? getCounterCurrencyCode() : getBaseCurrencyCode();
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Nullable
    public Map<String, String> getExtraDataMap() {
        return offerPayloadExtraDataMap != null ? offerPayloadExtraDataMap.getMap() : null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected static final CanonicalMapEntryIterator<String, String> SOURCE_ITERATION_ORDER = List::iterator;

    protected static <T extends OfferPayloadBase> CanonicalSchema.Builder<T> getBaseOfferPayloadSchemaBuilder() {
        return CanonicalSchema.<T>newBuilder()
                .string(1, offerPayload -> offerPayload.id)
                .int64(2, offerPayload -> offerPayload.date)
                .compose(3, OfferPayloadBase::getOwnerNodeAddressForCanonical, NodeAddress.SCHEMA)
                .compose(4, OfferPayloadBase::getPubKeyRingForCanonical, PubKeyRing.SCHEMA)
                .enumField(5, offerPayload -> offerPayload.direction)
                .int64(6, offerPayload -> offerPayload.price);
    }

    protected NodeAddress getOwnerNodeAddressForCanonical() {
        return checkNotNull(ownerNodeAddress,
                "OfferPayload is in invalid state: ownerNodeAddress is not set when adding to P2P network.");
    }

    protected PubKeyRing getPubKeyRingForCanonical() {
        return checkNotNull(pubKeyRing,
                "OfferPayload is in invalid state: pubKeyRing is not set when adding to P2P network.");
    }

    protected Map<String, String> getExtraDataMapForCanonical() {
        return offerPayloadExtraDataMap == null ? Collections.emptyMap() : offerPayloadExtraDataMap.getMap();
    }

    @Override
    public String toString() {
        return "OfferPayloadBase{" +
                "\r\n     id='" + id + '\'' +
                ",\r\n     date=" + date +
                ",\r\n     baseCurrencyCode='" + baseCurrencyCode + '\'' +
                ",\r\n     counterCurrencyCode='" + counterCurrencyCode + '\'' +
                ",\r\n     price=" + price +
                ",\r\n     amount=" + amount +
                ",\r\n     minAmount=" + minAmount +
                ",\r\n     paymentMethodId='" + paymentMethodId + '\'' +
                ",\r\n     makerPaymentAccountId='" + makerPaymentAccountId + '\'' +
                ",\r\n     ownerNodeAddress=" + ownerNodeAddress +
                ",\r\n     direction=" + direction +
                ",\r\n     versionNr='" + versionNr + '\'' +
                ",\r\n     protocolVersion=" + protocolVersion +
                ",\r\n     pubKeyRing=" + pubKeyRing +
                ",\r\n     hash=" + (hash != null ? Hex.encode(hash) : "null") +
                ",\r\n     offerPayloadExtraDataMap=" + offerPayloadExtraDataMap +
                "\r\n}";
    }
}
