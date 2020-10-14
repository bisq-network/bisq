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
import bisq.core.offer.OfferUtil;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateSortedTruncatablePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Hash;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;

import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This new trade statistics class uses only the bare minimum of data.
 * Data size is about 50 bytes in average
 */
@Slf4j
@Getter
public final class TradeStatistics3 implements ProcessOncePersistableNetworkPayload, PersistableNetworkPayload,
        CapabilityRequiringPayload, DateSortedTruncatablePayload {

    // This enum must not change the order as we use the ordinal for storage to reduce data size.
    // The payment method string can be quite long and would consume 15% more space.
    // When we get a new payment method we can add it to the enum at the end. Old users would add it as string if not
    // recognized.
    private enum PaymentMethodMapper {
        OK_PAY,
        CASH_APP,
        VENMO,
        AUSTRALIA_PAYID, // seems there is a dev trade
        UPHOLD,
        MONEY_BEAM,
        POPMONEY,
        REVOLUT,
        PERFECT_MONEY,
        SEPA,
        SEPA_INSTANT,
        FASTER_PAYMENTS,
        NATIONAL_BANK,
        JAPAN_BANK,
        SAME_BANK,
        SPECIFIC_BANKS,
        SWISH,
        ALI_PAY,
        WECHAT_PAY,
        CLEAR_X_CHANGE,
        CHASE_QUICK_PAY,
        INTERAC_E_TRANSFER,
        US_POSTAL_MONEY_ORDER,
        CASH_DEPOSIT,
        MONEY_GRAM,
        WESTERN_UNION,
        HAL_CASH,
        F2F,
        BLOCK_CHAINS,
        PROMPT_PAY,
        ADVANCED_CASH,
        BLOCK_CHAINS_INSTANT
    }

    private final String currency;
    private final long price;
    private final long amount;
    private final String paymentMethod;
    // As only seller is publishing it is the sellers trade date
    private final long date;

    // Old converted trade stat objects might not have it set
    @Nullable
    @JsonExclude
    private String mediator;
    @Nullable
    @JsonExclude
    private String refundAgent;

    // todo should we add referrerId as well? get added to extra map atm but not used so far

    // Hash get set in constructor from json of all the other data fields (with hash = null).
    @JsonExclude
    private final byte[] hash;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    @JsonExclude
    private final Map<String, String> extraDataMap;

    public TradeStatistics3(String currency,
                            long price,
                            long amount,
                            String paymentMethod,
                            long date,
                            String mediator,
                            String refundAgent,
                            @Nullable Map<String, String> extraDataMap) {
        this(currency,
                price,
                amount,
                paymentMethod,
                date,
                mediator,
                refundAgent,
                extraDataMap,
                null);
    }

    // Used from conversion method where we use the hash of the TradeStatistics2 objects to avoid duplicate entries
    public TradeStatistics3(String currency,
                            long price,
                            long amount,
                            String paymentMethod,
                            long date,
                            String mediator,
                            String refundAgent,
                            @Nullable byte[] hash) {
        this(currency,
                price,
                amount,
                paymentMethod,
                date,
                mediator,
                refundAgent,
                null,
                hash);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    public TradeStatistics3(String currency,
                            long price,
                            long amount,
                            String paymentMethod,
                            long date,
                            @Nullable String mediator,
                            @Nullable String refundAgent,
                            @Nullable Map<String, String> extraDataMap,
                            @Nullable byte[] hash) {
        this.currency = currency;
        this.price = price;
        this.amount = amount;
        String tempPaymentMethod;
        try {
            tempPaymentMethod = String.valueOf(PaymentMethodMapper.valueOf(paymentMethod).ordinal());
        } catch (Throwable t) {
            tempPaymentMethod = paymentMethod;
        }
        this.paymentMethod = tempPaymentMethod;
        this.date = date;
        this.mediator = mediator;
        this.refundAgent = refundAgent;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);

        this.hash = hash == null ? createHash() : hash;
    }

    public byte[] createHash() {
        // We create hash from all fields excluding hash itself. We use json as simple data serialisation.
        // TradeDate is different for both peers so we ignore it for hash. ExtraDataMap is ignored as well as at
        // software updates we might have different entries which would cause a different hash.
        return Hash.getSha256Ripemd160hash(Utilities.objectToJson(this).getBytes(Charsets.UTF_8));
    }

    private protobuf.TradeStatistics3.Builder getBuilder() {
        protobuf.TradeStatistics3.Builder builder = protobuf.TradeStatistics3.newBuilder()
                .setCurrency(currency)
                .setPrice(price)
                .setAmount(amount)
                .setPaymentMethod(paymentMethod)
                .setDate(date)
                .setHash(ByteString.copyFrom(hash));
        Optional.ofNullable(mediator).ifPresent(builder::setMediator);
        Optional.ofNullable(refundAgent).ifPresent(builder::setRefundAgent);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    public protobuf.TradeStatistics3 toProtoTradeStatistics3() {
        return getBuilder().build();
    }

    @Override
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        return protobuf.PersistableNetworkPayload.newBuilder().setTradeStatistics3(getBuilder()).build();
    }

    public static TradeStatistics3 fromProto(protobuf.TradeStatistics3 proto) {
        return new TradeStatistics3(
                proto.getCurrency(),
                proto.getPrice(),
                proto.getAmount(),
                proto.getPaymentMethod(),
                proto.getDate(),
                ProtoUtil.stringOrNullFromProto(proto.getMediator()),
                ProtoUtil.stringOrNullFromProto(proto.getRefundAgent()),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap(),
                proto.getHash().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public boolean verifyHashSize() {
        checkNotNull(hash, "hash must not be null");
        return hash.length == 20;
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.TRADE_STATISTICS_3);
    }

    @Override
    public Date getDate() {
        return getTradeDate();
    }

    @Override
    public int maxItems() {
        return 3000;
    }

    public void pruneOptionalData() {
        mediator = null;
        refundAgent = null;
    }

    public String getPaymentMethod() {
        try {
            return PaymentMethodMapper.values()[Integer.parseInt(paymentMethod)].name();
        } catch (Throwable ignore) {
            return paymentMethod;
        }
    }

    public Date getTradeDate() {
        return new Date(date);
    }

    public Price getTradePrice() {
        return Price.valueOf(currency, price);
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(amount);
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
        return amount > 0 &&
                price > 0 &&
                date > 0 &&
                paymentMethod != null &&
                !paymentMethod.isEmpty() &&
                currency != null &&
                !currency.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeStatistics3)) return false;

        TradeStatistics3 that = (TradeStatistics3) o;

        if (price != that.price) return false;
        if (amount != that.amount) return false;
        if (date != that.date) return false;
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) return false;
        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null)
            return false;
        return Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        int result = currency != null ? currency.hashCode() : 0;
        result = 31 * result + (int) (price ^ (price >>> 32));
        result = 31 * result + (int) (amount ^ (amount >>> 32));
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (int) (date ^ (date >>> 32));
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }

    @Override
    public String toString() {
        return "TradeStatistics3{" +
                "\n     currency='" + currency + '\'' +
                ",\n     price=" + price +
                ",\n     amount=" + amount +
                ",\n     paymentMethod='" + paymentMethod + '\'' +
                ",\n     date=" + date +
                ",\n     mediator='" + mediator + '\'' +
                ",\n     refundAgent='" + refundAgent + '\'' +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}
