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

import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.Sig;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.JsonExclude;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import org.springframework.util.CollectionUtils;

import java.security.PublicKey;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * @deprecated Was used in pre v0.6.0 version
 */
@Deprecated
@Slf4j
@EqualsAndHashCode(exclude = {"signaturePubKeyBytes"})
@Value
public final class TradeStatistics implements ProcessOncePersistableNetworkPayload, ProtectedStoragePayload, ExpirablePayload, PersistablePayload {
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
    private final long tradeDate;
    private final String depositTxId;
    @JsonExclude
    private final byte[] signaturePubKeyBytes;
    @JsonExclude
    transient private final PublicKey signaturePubKey;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public TradeStatistics(OfferPayload offerPayload,
                           Price tradePrice,
                           Coin tradeAmount,
                           Date tradeDate,
                           String depositTxId,
                           byte[] signaturePubKeyBytes) {
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
                signaturePubKeyBytes,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    TradeStatistics(OfferPayload.Direction direction,
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
                    byte[] signaturePubKeyBytes,
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
        this.signaturePubKeyBytes = signaturePubKeyBytes;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);

        signaturePubKey = Sig.getPublicKeyFromBytes(signaturePubKeyBytes);
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        final protobuf.TradeStatistics.Builder builder = protobuf.TradeStatistics.newBuilder()
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
                .setSignaturePubKeyBytes(ByteString.copyFrom(signaturePubKeyBytes));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return protobuf.StoragePayload.newBuilder().setTradeStatistics(builder).build();
    }

    public protobuf.TradeStatistics toProtoTradeStatistics() {
        return toProtoMessage().getTradeStatistics();
    }

    public static TradeStatistics fromProto(protobuf.TradeStatistics proto) {
        return new TradeStatistics(
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
                proto.getSignaturePubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return signaturePubKey;
    }

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
        if (getTradePrice().getMonetary() instanceof Altcoin)
            return new Volume(new AltcoinExchangeRate((Altcoin) getTradePrice().getMonetary()).coinToAltcoin(getTradeAmount()));
        else
            return new Volume(new ExchangeRate((Fiat) getTradePrice().getMonetary()).coinToFiat(getTradeAmount()));
    }
}
