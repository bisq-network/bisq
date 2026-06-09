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

package bisq.core.dao.governance.proposal.storage.temp;

import bisq.core.dao.state.model.governance.Proposal;

import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.Sig;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.TreeMapIterator;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * TempProposalPayload is wrapper for proposal sent over wire as well as it gets persisted.
 * Data size: about 1.245 bytes (pubKey makes it big)
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TempProposalPayload implements ProcessOncePersistableNetworkPayload, ProtectedStoragePayload,
        ExpirablePayload, PersistablePayload {
    // We keep data 2 months to be safe if we increase durations of cycle. Also give a bit more resilience in case
    // of any issues with the append-only data store
    public static final long TTL = TimeUnit.DAYS.toMillis(60);

    protected final Proposal proposal;
    protected final byte[] ownerPubKeyEncoded;
    @Nullable
    protected final TreeMap<String, String> extraDataMap;

    // Used just for caching. Don't persist.
    private final transient PublicKey ownerPubKey;

    public TempProposalPayload(Proposal proposal,
                               PublicKey ownerPublicKey) {
        this(proposal, Sig.getPublicKeyBytes(ownerPublicKey), null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TempProposalPayload(Proposal proposal,
                                byte[] ownerPubPubKeyEncoded,
                                @Nullable TreeMap<String, String> extraDataMap) {
        this.proposal = proposal;
        this.ownerPubKeyEncoded = ownerPubPubKeyEncoded;
        Map<String, String> validatedExtraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.extraDataMap = validatedExtraDataMap == null ? null : new TreeMap<>(validatedExtraDataMap);

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyEncoded);
    }

    private protobuf.TempProposalPayload.Builder getTempProposalPayloadBuilder() {
        final protobuf.TempProposalPayload.Builder builder = protobuf.TempProposalPayload.newBuilder()
                .setProposal(proposal.getProposalBuilder())
                .setOwnerPubKeyEncoded(ByteString.copyFrom(ownerPubKeyEncoded));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        return protobuf.StoragePayload.newBuilder().setTempProposalPayload(getTempProposalPayloadBuilder()).build();
    }

    public static TempProposalPayload fromProto(protobuf.TempProposalPayload proto) {
        return new TempProposalPayload(Proposal.fromProto(proto.getProposal()),
                proto.getOwnerPubKeyEncoded().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : new TreeMap<>(proto.getExtraDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final CanonicalSchema<TempProposalPayload> SCHEMA =
            CanonicalSchema.oneof("StoragePayload",
                    8,
                    CanonicalSchema.<TempProposalPayload>newBuilder()
                            .compose(1, TempProposalPayload::getProposal, Proposal.getProposalSchemaBuilder().build())
                            .bytes(2, TempProposalPayload::getOwnerPubKeyEncoded)
                            .mapStringToString(3,
                                    TempProposalPayload::getExtraDataMapForCanonical,
                                    TreeMapIterator.naturalOrder()));

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }

    private Map<String, String> getExtraDataMapForCanonical() {
        return extraDataMap == null ? Collections.emptyMap() : extraDataMap;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TempStoragePayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PublicKey getOwnerPubKey() {
        return ownerPubKey;
    }

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ExpirablePayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }
}
