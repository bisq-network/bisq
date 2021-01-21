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

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.JsonExclude;

import java.security.PublicKey;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class AtomicOfferPayload extends OfferPayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final String id;
    private final long date;
    private final NodeAddress ownerNodeAddress;
    @JsonExclude
    private final PubKeyRing pubKeyRing;
    private final Direction direction;
    private final long price;
    private final long amount;
    private final long minAmount;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AtomicOfferPayload(String id,
                              long date,
                              NodeAddress ownerNodeAddress,
                              PubKeyRing pubKeyRing,
                              Direction direction,
                              long price,
                              long amount,
                              long minAmount) {
        this.id = id;
        this.date = date;
        this.ownerNodeAddress = ownerNodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.minAmount = minAmount;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static protobuf.Direction toProtoMessage(Direction direction) {
        return protobuf.Direction.valueOf(direction.name());
    }

    public static Direction fromProto(protobuf.Direction direction) {
        return ProtoUtil.enumFromProto(OfferPayload.Direction.class, direction.name());
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        protobuf.AtomicOfferPayload.Builder builder = protobuf.AtomicOfferPayload.newBuilder()
                .setId(id)
                .setDate(date)
                .setOwnerNodeAddress(ownerNodeAddress.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setDirection(toProtoMessage(direction))
                .setPrice(price)
                .setAmount(amount)
                .setMinAmount(minAmount);

        return protobuf.StoragePayload.newBuilder().setAtomicOfferPayload(builder).build();
    }

    public static AtomicOfferPayload fromProto(protobuf.AtomicOfferPayload proto) {
        return new AtomicOfferPayload(proto.getId(),
                proto.getDate(),
                NodeAddress.fromProto(proto.getOwnerNodeAddress()),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                fromProto(proto.getDirection()),
                proto.getPrice(),
                proto.getAmount(),
                proto.getMinAmount());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getBaseCurrencyCode() {
        return "BSQ";
    }

    @Override
    public String getCounterCurrencyCode() {
        return "BTC";
    }

    @Nullable
    @Override
    public String getHashOfChallenge() {
        return null;
    }

    @Override
    public boolean isCurrencyForMakerFeeBtc() {
        return false;
    }

    @Override
    public boolean isPrivateOffer() {
        return false;
    }

    @Override
    public long getMakerFee() {
        return 0;
    }

    @Override
    public long getTxFee() {
        return 0;
    }

    @Override
    public String getMakerPaymentAccountId() {
        return null;
    }

    @Override
    public long getBuyerSecurityDeposit() {
        return 0;
    }

    @Override
    public long getSellerSecurityDeposit() {
        return 0;
    }

    @Override
    public long getMaxTradeLimit() {
        return 0;
    }

    @Override
    public long getMaxTradePeriod() {
        return 0;
    }

    @Override
    public String getPaymentMethodId() {
        return null;
    }

    @Override
    public double getMarketPriceMargin() {
        return 0;
    }

    @Override
    public boolean isUseMarketBasedPrice() {
        return false;
    }

    @Override
    public int getProtocolVersion() {
        return 0;
    }

    @Override
    public String getVersionNr() {
        return null;
    }

    @Override
    @Nullable
    public List<String> getAcceptedBankIds() {
        return null;
    }

    @Override
    @Nullable
    public String getBankId() {
        return null;
    }

    @Override
    @Nullable
    public List<String> getAcceptedCountryCodes() {
        return null;
    }

    @Override
    @Nullable
    public String getCountryCode() {
        return null;
    }

    @Override
    public boolean isUseAutoClose() {
        return false;
    }

    // TODO: perhaps this should be included as part of no fee tx offers
    @Override
    public long getBlockHeightAtOfferCreation() {
        return 0;
    }

    @Override
    public long getLowerClosePrice() {
        return 0;
    }

    @Override
    public long getUpperClosePrice() {
        return 0;
    }

    @Override
    public boolean isUseReOpenAfterAutoClose() {
        return false;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return null;
    }

    @Override
    public String toString() {
        return "OfferPayload{" +
                "\n     id='" + id + '\'' +
                ",\n     date=" + new Date(date) +
                ",\n     ownerNodeAddress=" + ownerNodeAddress +
                ",\n     pubKeyRing=" + pubKeyRing +
                ",\n     direction=" + direction +
                ",\n     price=" + price +
                ",\n     amount=" + amount +
                ",\n     minAmount=" + minAmount +
                "\n}";
    }
}
