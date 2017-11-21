package io.bisq.core.trade.statistics;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.monetary.AltcoinExchangeRate;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.util.JsonExclude;
import io.bisq.core.offer.OfferPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.LazyProcessedPayload;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 *
 * @deprecated  Was used in pre v0.6.0 version
 */
@Deprecated
@Slf4j
@EqualsAndHashCode(exclude = {"signaturePubKeyBytes", "signaturePubKey"})
@Value
public final class TradeStatistics implements LazyProcessedPayload, ProtectedStoragePayload, PersistablePayload {
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
        this.extraDataMap = extraDataMap;

        signaturePubKey = Sig.getPublicKeyFromBytes(signaturePubKeyBytes);
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.TradeStatistics.Builder builder = PB.TradeStatistics.newBuilder()
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
        return PB.StoragePayload.newBuilder().setTradeStatistics(builder).build();
    }

    public PB.TradeStatistics toProtoTradeStatistics() {
        return toProtoMessage().getTradeStatistics();
    }

    public static TradeStatistics fromProto(PB.TradeStatistics proto) {
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
        return CurrencyUtil.isCryptoCurrency(baseCurrency) ? baseCurrency : counterCurrency;
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
