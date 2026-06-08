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

package bisq.core.offer.bsq_swap;

import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferPayloadBase;
import bisq.core.payment.BsqSwapAccount;
import bisq.core.payment.payload.PaymentMethod;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.ProofOfWorkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.ProofOfWork;
import bisq.common.crypto.PubKeyRing;
import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.proto.ProtoUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class BsqSwapOfferPayload extends OfferPayloadBase
        implements ProofOfWorkPayload, CapabilityRequiringPayload, Canonical {

    public static BsqSwapOfferPayload from(BsqSwapOfferPayload original,
                                           String offerId,
                                           ProofOfWork proofOfWork) {
        return new BsqSwapOfferPayload(offerId,
                original.getDate(),
                original.getOwnerNodeAddress(),
                original.getPubKeyRing(),
                original.getDirection(),
                original.getPrice(),
                original.getAmount(),
                original.getMinAmount(),
                proofOfWork,
                original.getVersionNr(),
                original.getProtocolVersion()
        );
    }

    private final ProofOfWork proofOfWork;

    public BsqSwapOfferPayload(String id,
                               long date,
                               NodeAddress ownerNodeAddress,
                               PubKeyRing pubKeyRing,
                               OfferDirection direction,
                               long price,
                               long amount,
                               long minAmount,
                               ProofOfWork proofOfWork,
                               String versionNr,
                               int protocolVersion) {
        super(id,
                date,
                ownerNodeAddress,
                pubKeyRing,
                "BSQ",
                "BTC",
                direction,
                price,
                amount,
                minAmount,
                PaymentMethod.BSQ_SWAP_ID,
                BsqSwapAccount.ID,
                null,
                versionNr,
                protocolVersion);

        this.proofOfWork = proofOfWork;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static protobuf.OfferDirection toProtoMessage(OfferDirection direction) {
        return protobuf.OfferDirection.valueOf(direction.name());
    }

    public static OfferDirection fromProto(protobuf.OfferDirection offerDirection) {
        return ProtoUtil.enumFromProto(OfferDirection.class, offerDirection.name());
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        protobuf.BsqSwapOfferPayload.Builder builder = protobuf.BsqSwapOfferPayload.newBuilder()
                .setId(id)
                .setDate(date)
                .setOwnerNodeAddress(ownerNodeAddress.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setDirection(toProtoMessage(direction))
                .setPrice(price)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setProofOfWork(proofOfWork.toProtoMessage())
                .setVersionNr(versionNr)
                .setProtocolVersion(protocolVersion);

        return protobuf.StoragePayload.newBuilder().setBsqSwapOfferPayload(builder).build();
    }

    public static BsqSwapOfferPayload fromProto(protobuf.BsqSwapOfferPayload proto) {
        // ExtraDataMap was always null and is not supported anymore since v1.10.2.
        // It is not expected that any historical data exist with a non-empty ExtraDataMap.
        checkArgument(proto.getExtraDataMap().isEmpty(),
                "ExtraDataMap is expected to be not set in BsqSwapOfferPayload");

        return new BsqSwapOfferPayload(proto.getId(),
                proto.getDate(),
                NodeAddress.fromProto(proto.getOwnerNodeAddress()),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                fromProto(proto.getDirection()),
                proto.getPrice(),
                proto.getAmount(),
                proto.getMinAmount(),
                ProofOfWork.fromProto(proto.getProofOfWork()),
                proto.getVersionNr(),
                proto.getProtocolVersion()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final CanonicalSchema<BsqSwapOfferPayload> PAYLOAD_SCHEMA =
            OfferPayloadBase.<BsqSwapOfferPayload>getBaseOfferPayloadSchemaBuilder()
                    .int64(7, offerPayload -> offerPayload.amount)
                    .int64(8, offerPayload -> offerPayload.minAmount)
                    .compose(9, BsqSwapOfferPayload::getProofOfWorkForCanonical, ProofOfWork.SCHEMA)
                    .string(11, offerPayload -> offerPayload.versionNr)
                    .int32(12, offerPayload -> offerPayload.protocolVersion)
                    .build();

    public static final CanonicalSchema<BsqSwapOfferPayload> SCHEMA =
            CanonicalSchema.<BsqSwapOfferPayload>newBuilder()
                    .extend(10, offerPayload -> offerPayload, PAYLOAD_SCHEMA)
                    .build();

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }

    private ProofOfWork getProofOfWorkForCanonical() {
        return checkNotNull(proofOfWork,
                "BsqSwapOfferPayload is in invalid state: proofOfWork is not set when adding to P2P network.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ProofOfWorkPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.BSQ_SWAP_OFFER);
    }

    @Override
    public String toString() {
        return "BsqSwapOfferPayload{" +
                "\r\n     proofOfWork=" + proofOfWork +
                "\r\n} " + super.toString();
    }
}
