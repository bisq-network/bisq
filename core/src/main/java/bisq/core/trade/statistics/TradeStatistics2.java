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

package bisq.core.trade.statistics;

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.AltcoinExchangeRate;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.LazyProcessedPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Hash;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import org.springframework.util.CollectionUtils;

import com.google.common.base.Charsets;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Serialized size is about 180-210 byte. Nov 2017 we have 5500 objects
 */

@Slf4j
@Value
public final class TradeStatistics2 implements LazyProcessedPayload, PersistableNetworkPayload, PersistableEnvelope, CapabilityRequiringPayload {
    public static final String ARBITRATOR_ADDRESS = "arbAddr";

    private final OfferPayload.Direction direction;
    private final String baseCurrency;
    private final String counterCurrency;
    private final String offerPaymentMethod;
    private final long offerDate;
    private final boolean offerUseMarketBasedPrice;
    private final double offerMarketPriceMargin;
    private final long offerAmount;
    private final long offerMinAmount;
    private final String offerId;
    private final long tradePrice;
    private final long tradeAmount;
    // tradeDate is different for both peers so we ignore it for hash
    @JsonExclude
    private final long tradeDate;
    private final String depositTxId;

    // hash get set in constructor from json of all the other data fields (with hash = null).
    private final byte[] hash;
    // PB field signature_pub_key_bytes not used anymore from v0.6 on

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public TradeStatistics2(OfferPayload offerPayload,
                            Price tradePrice,
                            Coin tradeAmount,
                            Date tradeDate,
                            String depositTxId,
                            Map<String, String> extraDataMap) {
        this(offerPayload.getDirection(),
                offerPayload.getBaseCurrencyCode(),
                offerPayload.getCounterCurrencyCode(),
                offerPayload.getPaymentMethodId(),
                offerPayload.getDate(),
                offerPayload.isUseMarketBasedPrice(),
                offerPayload.getMarketPriceMargin(),
                offerPayload.getAmount(),
                offerPayload.getMinAmount(),
                offerPayload.getId(),
                tradePrice.getValue(),
                tradeAmount.value,
                tradeDate.getTime(),
                depositTxId,
                null,
                extraDataMap);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeStatistics2(OfferPayload.Direction direction,
                            String baseCurrency,
                            String counterCurrency,
                            String offerPaymentMethod,
                            long offerDate,
                            boolean offerUseMarketBasedPrice,
                            double offerMarketPriceMargin,
                            long offerAmount,
                            long offerMinAmount,
                            String offerId,
                            long tradePrice,
                            long tradeAmount,
                            long tradeDate,
                            String depositTxId,
                            @Nullable byte[] hash,
                            @Nullable Map<String, String> extraDataMap) {
        this.direction = direction;
        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;
        this.offerPaymentMethod = offerPaymentMethod;
        this.offerDate = offerDate;
        this.offerUseMarketBasedPrice = offerUseMarketBasedPrice;
        this.offerMarketPriceMargin = offerMarketPriceMargin;
        this.offerAmount = offerAmount;
        this.offerMinAmount = offerMinAmount;
        this.offerId = offerId;
        this.tradePrice = tradePrice;
        this.tradeAmount = tradeAmount;
        this.tradeDate = tradeDate;
        this.depositTxId = depositTxId;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);

        if (hash == null)
            // We create hash from all fields excluding hash itself. We use json as simple data serialisation.
            // tradeDate is different for both peers so we ignore it for hash.
            this.hash = Hash.getSha256Ripemd160hash(Utilities.objectToJson(this).getBytes(Charsets.UTF_8));
        else
            this.hash = hash;
    }

    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        final PB.TradeStatistics2.Builder builder = PB.TradeStatistics2.newBuilder()
                .setDirection(OfferPayload.Direction.toProtoMessage(direction))
                .setBaseCurrency(baseCurrency)
                .setCounterCurrency(counterCurrency)
                .setPaymentMethodId(offerPaymentMethod)
                .setOfferDate(offerDate)
                .setOfferUseMarketBasedPrice(offerUseMarketBasedPrice)
                .setOfferMarketPriceMargin(offerMarketPriceMargin)
                .setOfferAmount(offerAmount)
                .setOfferMinAmount(offerMinAmount)
                .setOfferId(offerId)
                .setTradePrice(tradePrice)
                .setTradeAmount(tradeAmount)
                .setTradeDate(tradeDate)
                .setDepositTxId(depositTxId)
                .setHash(ByteString.copyFrom(hash));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return PB.PersistableNetworkPayload.newBuilder().setTradeStatistics2(builder).build();
    }


    public PB.TradeStatistics2 toProtoTradeStatistics2() {
        return toProtoMessage().getTradeStatistics2();
    }

    public static TradeStatistics2 fromProto(PB.TradeStatistics2 proto) {
        return new TradeStatistics2(
                OfferPayload.Direction.fromProto(proto.getDirection()),
                proto.getBaseCurrency(),
                proto.getCounterCurrency(),
                proto.getPaymentMethodId(),
                proto.getOfferDate(),
                proto.getOfferUseMarketBasedPrice(),
                proto.getOfferMarketPriceMargin(),
                proto.getOfferAmount(),
                proto.getOfferMinAmount(),
                proto.getOfferId(),
                proto.getTradePrice(),
                proto.getTradeAmount(),
                proto.getTradeDate(),
                proto.getDepositTxId(),
                proto.getHash().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.TRADE_STATISTICS_2);
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public boolean verifyHashSize() {
        checkNotNull(hash, "hash must not be null");
        return hash.length == 20;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Price getTradePrice() {
        return Price.valueOf(getCurrencyCode(), tradePrice);
    }

    public String getCurrencyCode() {
        return baseCurrency.equals("BTC") ? counterCurrency : baseCurrency;
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Volume getTradeVolume() {
        if (getTradePrice().getMonetary() instanceof Altcoin) {
            return new Volume(new AltcoinExchangeRate((Altcoin) getTradePrice().getMonetary()).coinToAltcoin(getTradeAmount()));
        } else {
            Volume volume = new Volume(new ExchangeRate((Fiat) getTradePrice().getMonetary()).coinToFiat(getTradeAmount()));
            return OfferUtil.getRoundedFiatVolume(volume);
        }
    }

    public boolean isValid() {
        return tradeAmount > 0 && tradePrice > 0;
    }

    @Override
    public String toString() {
        return "TradeStatistics2{" +
                "\n     direction=" + direction +
                ",\n     baseCurrency='" + baseCurrency + '\'' +
                ",\n     counterCurrency='" + counterCurrency + '\'' +
                ",\n     offerPaymentMethod='" + offerPaymentMethod + '\'' +
                ",\n     offerDate=" + offerDate +
                ",\n     offerUseMarketBasedPrice=" + offerUseMarketBasedPrice +
                ",\n     offerMarketPriceMargin=" + offerMarketPriceMargin +
                ",\n     offerAmount=" + offerAmount +
                ",\n     offerMinAmount=" + offerMinAmount +
                ",\n     offerId='" + offerId + '\'' +
                ",\n     tradePrice=" + tradePrice +
                ",\n     tradeAmount=" + tradeAmount +
                ",\n     tradeDate=" + tradeDate +
                ",\n     depositTxId='" + depositTxId + '\'' +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}
