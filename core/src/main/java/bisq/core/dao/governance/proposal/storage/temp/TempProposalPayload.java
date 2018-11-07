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

import bisq.core.dao.governance.proposal.Proposal;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.LazyProcessedPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.crypto.Sig;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import org.springframework.util.CollectionUtils;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class TempProposalPayload implements LazyProcessedPayload, ProtectedStoragePayload,
        ExpirablePayload, CapabilityRequiringPayload, PersistablePayload {

    protected final Proposal proposal;
    protected final byte[] ownerPubKeyEncoded;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    protected final Map<String, String> extraDataMap;

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
                                @Nullable Map<String, String> extraDataMap) {
        this.proposal = proposal;
        this.ownerPubKeyEncoded = ownerPubPubKeyEncoded;
        this.extraDataMap = extraDataMap;

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyEncoded);
    }

    private PB.TempProposalPayload.Builder getTempProposalPayloadBuilder() {
        final PB.TempProposalPayload.Builder builder = PB.TempProposalPayload.newBuilder()
                .setProposal(proposal.getProposalBuilder())
                .setOwnerPubKeyEncoded(ByteString.copyFrom(ownerPubKeyEncoded));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        return PB.StoragePayload.newBuilder().setTempProposalPayload(getTempProposalPayloadBuilder()).build();
    }

    public static TempProposalPayload fromProto(PB.TempProposalPayload proto) {
        return new TempProposalPayload(Proposal.fromProto(proto.getProposal()),
                proto.getOwnerPubKeyEncoded().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TempStoragePayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PublicKey getOwnerPubKey() {
        return ownerPubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CapabilityRequiringPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.PROPOSAL.ordinal()
        ));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ExpirablePayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        // We keep data 2 months to be safe if we increase durations of cycle. Also give a bit more resilience in case
        // of any issues with the append-only data store
        return TimeUnit.DAYS.toMillis(60);
    }
}
