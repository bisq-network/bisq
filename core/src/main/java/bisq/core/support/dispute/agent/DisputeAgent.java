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
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.Utilities;

import java.security.PublicKey;

import java.util.List;
import java.util.Map;
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

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    protected Map<String, String> extraDataMap;

    public DisputeAgent(NodeAddress nodeAddress,
                        PubKeyRing pubKeyRing,
                        List<String> languageCodes,
                        long registrationDate,
                        byte[] registrationPubKey,
                        String registrationSignature,
                        @Nullable String emailAddress,
                        @Nullable String info,
                        @Nullable Map<String, String> extraDataMap) {
        this.nodeAddress = nodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.languageCodes = languageCodes;
        this.registrationDate = registrationDate;
        this.registrationPubKey = registrationPubKey;
        this.registrationSignature = registrationSignature;
        this.emailAddress = emailAddress;
        this.info = info;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
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
