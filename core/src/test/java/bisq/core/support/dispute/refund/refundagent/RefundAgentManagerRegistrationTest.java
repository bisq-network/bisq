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

package bisq.core.support.dispute.refund.refundagent;

import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.app.DevEnv;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import java.math.BigInteger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The refund agents share the registry rules of the mediators, see MediatorManagerRegistrationTest. A refund agent
 * with a malformed key, which anyone can publish, is rejected without stopping the update of the registry.
 */
class RefundAgentManagerRegistrationTest {
    private static final ECKey REGISTRATION_KEY =
            ECKey.fromPrivate(new BigInteger(1, Utils.HEX.decode(DevEnv.getDEV_PRIVILEGE_PRIV_KEY())));
    private static final NodeAddress REFUND_AGENT_ADDRESS = new NodeAddress("refund-agent.onion", 9999);
    private static final NodeAddress OTHER_ADDRESS = new NodeAddress("other.onion", 9999);

    private final P2PService p2PService = mock(P2PService.class);
    private final User user = new User();
    private final RefundAgentManager manager = new RefundAgentManager(null,
            new RefundAgentService(p2PService, mock(FilterPolicyService.class)), user, mock(FilterManager.class), true);

    @Test
    void aRefundAgentWithAMalformedKeyIsRejected() {
        PubKeyRing pubKeyRing = pubKeyRing();
        RefundAgent registered = refundAgent(REFUND_AGENT_ADDRESS, pubKeyRing, REGISTRATION_KEY.getPubKey(),
                REGISTRATION_KEY.signMessage(Utils.HEX.encode(pubKeyRing.getSignaturePubKey().getEncoded())));
        givenRefundAgents(refundAgent(OTHER_ADDRESS, pubKeyRing(), new byte[0], "signature"), registered);

        manager.updateMap();

        assertEquals(Map.of(REFUND_AGENT_ADDRESS, registered), manager.getObservableMap());
        assertEquals(List.of(registered), user.getAcceptedRefundAgents());
    }

    private void givenRefundAgents(RefundAgent... refundAgents) {
        Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> dataMap = new LinkedHashMap<>();
        for (RefundAgent refundAgent : refundAgents) {
            ProtectedStorageEntry entry = mock(ProtectedStorageEntry.class);
            when(entry.getProtectedStoragePayload()).thenReturn(refundAgent);
            dataMap.put(new P2PDataStorage.ByteArray(new byte[]{(byte) dataMap.size()}), entry);
        }
        when(p2PService.getDataMap()).thenReturn(dataMap);
    }

    private static RefundAgent refundAgent(NodeAddress nodeAddress,
                                           PubKeyRing pubKeyRing,
                                           byte[] registrationPubKey,
                                           String signature) {
        return new RefundAgent(nodeAddress, pubKeyRing, List.of("en"), 0, registrationPubKey, signature, null, null);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }
}
