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

package bisq.core.support.dispute.agent;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.PubKeyRing;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.proto.network.GetDataResponsePriority;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.Utilities;

import java.security.PublicKey;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode
@Slf4j
@Getter
public abstract class DisputeAgent implements ProtectedStoragePayload, ExpirablePayload {
    public static final long TTL = TimeUnit.DAYS.toMillis(10);

    protected final NodeAddress nodeAddress;
    protected final PubKeyRing pubKeyRing;
    protected final List<String> languageCodes;
    protected final long registrationDate;
    protected final byte[] registrationPubKey;
    protected final String registrationSignature;
    @Nullable
    protected final String emailAddress;
    @Nullable
    protected final String info;
    @Nullable
    protected final TreeMap<String, String> extraDataMap;

    public DisputeAgent(NodeAddress nodeAddress,
                        PubKeyRing pubKeyRing,
                        List<String> languageCodes,
                        long registrationDate,
                        byte[] registrationPubKey,
                        String registrationSignature,
                        @Nullable String emailAddress,
                        @Nullable String info) {
        this(nodeAddress,
                pubKeyRing,
                languageCodes,
                registrationDate,
                registrationPubKey,
                registrationSignature,
                emailAddress,
                info,
                null);
    }

    public DisputeAgent(NodeAddress nodeAddress,
                        PubKeyRing pubKeyRing,
                        List<String> languageCodes,
                        long registrationDate,
                        byte[] registrationPubKey,
                        String registrationSignature,
                        @Nullable String emailAddress,
                        @Nullable String info,
                        @Nullable TreeMap<String, String> extraDataMap) {
        this.nodeAddress = nodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.languageCodes = languageCodes;
        this.registrationDate = registrationDate;
        this.registrationPubKey = registrationPubKey;
        this.registrationSignature = registrationSignature;
        this.emailAddress = emailAddress;
        this.info = info;
        Map<String, String> validatedExtraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.extraDataMap = validatedExtraDataMap == null ? null : new TreeMap<>(validatedExtraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected static <T extends DisputeAgent> CanonicalSchema.Builder<T> getDisputeAgentSchemaBuilder() {
        return CanonicalSchema.<T>newBuilder()
                .compose(1, disputeAgent -> disputeAgent.nodeAddress, NodeAddress.SCHEMA)
                .repeatedString(2, disputeAgent -> disputeAgent.languageCodes)
                .int64(3, disputeAgent -> disputeAgent.registrationDate)
                .string(4, disputeAgent -> disputeAgent.registrationSignature)
                .bytes(5, disputeAgent -> disputeAgent.registrationPubKey)
                .compose(6, disputeAgent -> disputeAgent.pubKeyRing, PubKeyRing.SCHEMA);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public GetDataResponsePriority getGetDataResponsePriority() {
        return GetDataResponsePriority.HIGH;
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
        return extraDataMap;
    }

    @Override
    public String toString() {
        return "DisputeAgent{" +
                "\n     nodeAddress=" + nodeAddress +
                ",\n     pubKeyRing=" + pubKeyRing +
                ",\n     languageCodes=" + languageCodes +
                ",\n     registrationDate=" + registrationDate +
                ",\n     registrationPubKey=" + Utilities.bytesAsHexString(registrationPubKey) +
                ",\n     registrationSignature='" + registrationSignature + '\'' +
                ",\n     emailAddress='" + emailAddress + '\'' +
                ",\n     info='" + info + '\'' +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}
