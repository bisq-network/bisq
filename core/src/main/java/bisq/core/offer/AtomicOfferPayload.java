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

import bisq.core.payment.payload.PaymentMethod;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.ProofOfWorkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import java.util.Date;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class AtomicOfferPayload extends OfferPayloadBase
        implements ProofOfWorkPayload, CapabilityRequiringPayload {

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
    private final String versionNr;
    private final int protocolVersion;
    private final byte[] proofOfWork;

    private transient byte[] hash;

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
                              long minAmount,
                              String versionNr,
                              int protocolVersion,
                              byte[] proofOfWork) {
        this.id = id;
        this.date = date;
        this.ownerNodeAddress = ownerNodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.minAmount = minAmount;
        this.versionNr = versionNr;
        this.protocolVersion = protocolVersion;
        this.proofOfWork = proofOfWork;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static protobuf.OfferDirection toProtoMessage(Direction direction) {
        return protobuf.OfferDirection.valueOf(direction.name());
    }

    public static Direction fromProto(protobuf.OfferDirection offerDirection) {
        return ProtoUtil.enumFromProto(Direction.class, offerDirection.name());
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
                .setMinAmount(minAmount)
                .setVersionNr(versionNr)
                .setProtocolVersion(protocolVersion)
                .setProofOfWork(ByteString.copyFrom(proofOfWork));

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
                proto.getMinAmount(),
                proto.getVersionNr(),
                proto.getProtocolVersion(),
                proto.getProofOfWork().toByteArray());
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

    // Cannot be used for offers without fee tx since the fee depends on the trade
    // amount taken
    @Override
    public long getMakerFee() {
        return 0;
    }

    @Override
    public String getMakerPaymentAccountId() {
        return null;
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
        return PaymentMethod.ATOMIC_ID;
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
    public byte[] getHash() {
        if (this.hash == null) {
            this.hash = Hash.getSha256Hash(this.toProtoMessage().toByteArray());
        }
        return this.hash;
    }

    @Override
    public String toString() {
        return "OfferPayload{" +
                "\n     id='" + id + '\'' +
                ",\n     date=" + new Date(date) +
                ",\n     ownerNodeAddress=" + ownerNodeAddress +
                ",\n     pubKeyRing=" + pubKeyRing +
                ",\n     direction=" + direction +
                ",\n     amount=" + amount +
                ",\n     minAmount=" + minAmount +
                ",\n     versionNr='" + versionNr +
                ",\n     protocolVersion=" + protocolVersion +
                ",\n     proofOfWork=" + Utilities.bytesAsHexString(proofOfWork) +
                "\n}";
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.BSQ_SWAP_OFFER);
    }
}
