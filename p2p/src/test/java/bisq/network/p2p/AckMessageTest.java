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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AckMessageTest {
    @Test
    public void ackMessageIsSenderNodeAddressAwareEnvelope() {
        NodeAddress senderNodeAddress = new NodeAddress("sender.onion", 9999);

        AckMessage ackMessage = new AckMessage(senderNodeAddress,
                AckMessageSourceType.TRADE_MESSAGE,
                "TestMessage",
                "sourceUid",
                "sourceId",
                true,
                null);

        assertTrue(ackMessage instanceof SendersNodeAddressAwareEnvelope);
        assertEquals(senderNodeAddress, ((SendersNodeAddressAwareEnvelope) ackMessage).getSenderNodeAddress());
    }
}
