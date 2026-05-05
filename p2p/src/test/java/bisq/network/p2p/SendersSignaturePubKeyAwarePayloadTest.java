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

import bisq.network.p2p.mocks.MockPayload;

import bisq.common.proto.network.NetworkEnvelope;

import org.apache.commons.lang3.NotImplementedException;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SendersSignaturePubKeyAwarePayloadTest {
    @Test
    public void payloadWithoutSenderSignaturePubKeyDoesNotImplementInterface() {
        NetworkEnvelope payload = new MockPayload("msg");

        assertFalse(payload instanceof SendersSignaturePubKeyAwarePayload);
    }

    @Test
    public void senderAwarePayloadMatchesExpectedSenderSignaturePubKey() throws NoSuchAlgorithmException {
        PublicKey expectedSenderSignaturePubKey = TestUtils.generateKeyPair().getPublic();
        SignaturePubKeyAwarePayload payload = new SignaturePubKeyAwarePayload(expectedSenderSignaturePubKey);

        assertTrue(payload instanceof SendersSignaturePubKeyAwarePayload);
        assertEquals(expectedSenderSignaturePubKey, payload.getSenderSignaturePubKey());
        assertTrue(SendersSignaturePubKeyAwarePayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                expectedSenderSignaturePubKey));
    }

    @Test
    public void senderAwarePayloadDoesNotMatchDifferentSenderSignaturePubKey() throws NoSuchAlgorithmException {
        SignaturePubKeyAwarePayload payload = new SignaturePubKeyAwarePayload(TestUtils.generateKeyPair().getPublic());
        PublicKey expectedSenderSignaturePubKey = TestUtils.generateKeyPair().getPublic();

        assertFalse(SendersSignaturePubKeyAwarePayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                expectedSenderSignaturePubKey));
    }

    @Test
    public void senderAwarePayloadDoesNotMatchNullExpectedSenderSignaturePubKey() throws NoSuchAlgorithmException {
        SignaturePubKeyAwarePayload payload = new SignaturePubKeyAwarePayload(TestUtils.generateKeyPair().getPublic());

        assertFalse(SendersSignaturePubKeyAwarePayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                null));
    }

    private static class SignaturePubKeyAwarePayload extends NetworkEnvelope
            implements SendersSignaturePubKeyAwarePayload {
        private final PublicKey senderSignaturePubKey;

        private SignaturePubKeyAwarePayload(PublicKey senderSignaturePubKey) {
            super(0);
            this.senderSignaturePubKey = senderSignaturePubKey;
        }

        @Override
        public PublicKey getSenderSignaturePubKey() {
            return senderSignaturePubKey;
        }

        @Override
        public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
            throw new NotImplementedException("toProtoNetworkEnvelope not impl.");
        }
    }
}
