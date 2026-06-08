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

import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.encoding.canonical.CanonicalEncoder;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class DisputeAgentCanonicalEncoderTest {
    @Test
    @SuppressWarnings("deprecation")
    void arbitratorSerializeForHashMatchesProtobufForCanonicalSchema() {
        Arbitrator arbitrator = new Arbitrator(new NodeAddress("arbitrator.onion", 9999),
                new byte[]{0x01, 0x02, 0x03},
                "btc-address",
                pubKeyRing(),
                List.of("en", "de"),
                1_700_000_000_000L,
                new byte[]{0x04, 0x05, 0x06},
                "registration-signature",
                "arbitrator@example.com",
                "arbitrator-info");

        assertCanonicalMatchesProtobuf(arbitrator);
    }

    @Test
    void mediatorSerializeForHashMatchesProtobufForCanonicalSchema() {
        Mediator mediator = new Mediator(new NodeAddress("mediator.onion", 9998),
                pubKeyRing(),
                List.of("en", "es"),
                1_700_000_000_001L,
                new byte[]{0x07, 0x08, 0x09},
                "registration-signature",
                null,
                "mediator-info");

        assertCanonicalMatchesProtobuf(mediator);
    }

    @Test
    void refundAgentSerializeForHashMatchesProtobufForCanonicalSchema() {
        RefundAgent refundAgent = new RefundAgent(new NodeAddress("refund-agent.onion", 9997),
                pubKeyRing(),
                List.of("en", "fr"),
                1_700_000_000_002L,
                new byte[]{0x0a, 0x0b, 0x0c},
                "registration-signature",
                "refund-agent@example.com",
                null);

        assertCanonicalMatchesProtobuf(refundAgent);
    }

    private static void assertCanonicalMatchesProtobuf(DisputeAgent disputeAgent) {
        assertArrayEquals(disputeAgent.serialize(), disputeAgent.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(disputeAgent.encodeCanonical(CanonicalEncoder.DEFAULT), disputeAgent.serializeForHash());
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }
}
