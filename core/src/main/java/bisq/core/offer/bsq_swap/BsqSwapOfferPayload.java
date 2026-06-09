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
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.TreeMapIterator;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class BsqSwapOfferPayload extends OfferPayloadBase
        implements ProofOfWorkPayload, CapabilityRequiringPayload {

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
                copyExtraDataMap(original.getExtraDataMap()),
                original.getVersionNr(),
                original.getProtocolVersion()
        );
    }

    private final ProofOfWork proofOfWork;
    @Nullable
    private final TreeMap<String, String> extraDataMap;

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
        this(id,
                date,
                ownerNodeAddress,
                pubKeyRing,
                direction,
                price,
                amount,
                minAmount,
                proofOfWork,
                null,
                versionNr,
                protocolVersion);
    }

    public BsqSwapOfferPayload(String id,
                               long date,
                               NodeAddress ownerNodeAddress,
                               PubKeyRing pubKeyRing,
                               OfferDirection direction,
                               long price,
                               long amount,
                               long minAmount,
                               ProofOfWork proofOfWork,
                               @Nullable TreeMap<String, String> extraDataMap,
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
        Map<String, String> validatedExtraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.extraDataMap = validatedExtraDataMap == null ? null : new TreeMap<>(validatedExtraDataMap);
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
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);

        return protobuf.StoragePayload.newBuilder().setBsqSwapOfferPayload(builder).build();
    }

    public static BsqSwapOfferPayload fromProto(protobuf.BsqSwapOfferPayload proto) {
        return new BsqSwapOfferPayload(proto.getId(),
                proto.getDate(),
                NodeAddress.fromProto(proto.getOwnerNodeAddress()),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                fromProto(proto.getDirection()),
                proto.getPrice(),
                proto.getAmount(),
                proto.getMinAmount(),
                ProofOfWork.fromProto(proto.getProofOfWork()),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : new TreeMap<>(proto.getExtraDataMap()),
                proto.getVersionNr(),
                proto.getProtocolVersion()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final CanonicalSchema<BsqSwapOfferPayload> SCHEMA =
            CanonicalSchema.oneof("StoragePayload",
                    10,
                    OfferPayloadBase.<BsqSwapOfferPayload>getBaseOfferPayloadSchemaBuilder()
                            .int64(7, offerPayload -> offerPayload.amount)
                            .int64(8, offerPayload -> offerPayload.minAmount)
                            .compose(9, BsqSwapOfferPayload::getProofOfWorkForCanonical, ProofOfWork.SCHEMA)
                            .mapStringToString(10,
                                    BsqSwapOfferPayload::getExtraDataMapForCanonical,
                                    TreeMapIterator.naturalOrder())
                            .string(11, offerPayload -> offerPayload.versionNr)
                            .int32(12, offerPayload -> offerPayload.protocolVersion));

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }

    private ProofOfWork getProofOfWorkForCanonical() {
        return checkNotNull(proofOfWork,
                "BsqSwapOfferPayload is in invalid state: proofOfWork is not set when adding to P2P network.");
    }

    @Override
    protected Map<String, String> getExtraDataMapForCanonical() {
        return extraDataMap == null ? Collections.emptyMap() : extraDataMap;
    }

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
    }

    @Nullable
    private static TreeMap<String, String> copyExtraDataMap(@Nullable Map<String, String> extraDataMap) {
        return extraDataMap == null ? null : new TreeMap<>(extraDataMap);
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
                "\r\n     extraDataMap=" + extraDataMap +
                "\r\n} " + super.toString();
    }
}
