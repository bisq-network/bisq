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

package bisq.network.p2p;

import bisq.network.p2p.mocks.MockMailboxPayload;
import bisq.network.p2p.mocks.MockPayload;

import bisq.common.proto.network.NetworkEnvelope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SendersNodeAddressAwarePayloadTest {
    private static final NodeAddress EXPECTED_SENDER = new NodeAddress("sender.onion", 9999);
    private static final NodeAddress OTHER_SENDER = new NodeAddress("other.onion", 9999);

    @Test
    public void payloadWithoutSenderNodeAddressDoesNotImplementInterface() {
        NetworkEnvelope payload = new MockPayload("msg");

        assertFalse(payload instanceof SendersNodeAddressAwarePayload);
    }

    @Test
    public void senderAwarePayloadMatchesExpectedSenderNodeAddress() {
        MockMailboxPayload payload = new MockMailboxPayload("msg", EXPECTED_SENDER);

        assertTrue(payload instanceof SendersNodeAddressAwarePayload);
        assertEquals(EXPECTED_SENDER, payload.getSenderNodeAddress());
        assertTrue(SendersNodeAddressAwarePayload.isSenderNodeAddressMatching(payload.getSenderNodeAddress(),
                EXPECTED_SENDER));
    }

    @Test
    public void senderAwarePayloadDoesNotMatchDifferentSenderNodeAddress() {
        MockMailboxPayload payload = new MockMailboxPayload("msg", OTHER_SENDER);

        assertFalse(SendersNodeAddressAwarePayload.isSenderNodeAddressMatching(payload.getSenderNodeAddress(),
                EXPECTED_SENDER));
    }

    @Test
    public void senderAwarePayloadDoesNotMatchNullExpectedSenderNodeAddress() {
        MockMailboxPayload payload = new MockMailboxPayload("msg", EXPECTED_SENDER);

        assertFalse(SendersNodeAddressAwarePayload.isSenderNodeAddressMatching(payload.getSenderNodeAddress(), null));
    }
}
