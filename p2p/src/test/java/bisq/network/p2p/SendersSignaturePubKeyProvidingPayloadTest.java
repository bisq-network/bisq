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

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SendersSignaturePubKeyProvidingPayloadTest {
    @Test
    public void payloadWithoutSenderSignaturePubKeyDoesNotImplementInterface() {
        NetworkEnvelope payload = new MockPayload("msg");

        assertFalse(payload instanceof SendersSignaturePubKeyProvidingPayload);
    }

    @Test
    public void senderProvidingPayloadMatchesExpectedSenderSignaturePubKey() throws NoSuchAlgorithmException {
        PublicKey expectedSenderSignaturePubKey = TestUtils.generateKeyPair().getPublic();
        SignaturePubKeyProvidingPayload payload = new SignaturePubKeyProvidingPayload(expectedSenderSignaturePubKey);

        assertTrue(payload instanceof SendersSignaturePubKeyProvidingPayload);
        assertEquals(expectedSenderSignaturePubKey, payload.getSenderSignaturePubKey());
        assertTrue(SendersSignaturePubKeyProvidingPayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                expectedSenderSignaturePubKey,
                payload.isSenderSignaturePubKeyRequired()));
    }

    @Test
    public void senderProvidingPayloadDoesNotMatchDifferentSenderSignaturePubKey() throws NoSuchAlgorithmException {
        SignaturePubKeyProvidingPayload payload = new SignaturePubKeyProvidingPayload(TestUtils.generateKeyPair().getPublic());
        PublicKey expectedSenderSignaturePubKey = TestUtils.generateKeyPair().getPublic();

        assertFalse(SendersSignaturePubKeyProvidingPayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                expectedSenderSignaturePubKey,
                payload.isSenderSignaturePubKeyRequired()));
    }

    @Test
    public void senderProvidingPayloadDoesNotMatchNullExpectedSenderSignaturePubKey() throws NoSuchAlgorithmException {
        SignaturePubKeyProvidingPayload payload = new SignaturePubKeyProvidingPayload(TestUtils.generateKeyPair().getPublic());

        assertFalse(SendersSignaturePubKeyProvidingPayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                null,
                payload.isSenderSignaturePubKeyRequired()));
    }

    @Test
    public void senderProvidingPayloadRejectsMissingRequiredSenderSignaturePubKey() throws NoSuchAlgorithmException {
        SignaturePubKeyProvidingPayload payload = new SignaturePubKeyProvidingPayload(null);

        assertFalse(SendersSignaturePubKeyProvidingPayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                TestUtils.generateKeyPair().getPublic(),
                payload.isSenderSignaturePubKeyRequired()));
    }

    @Test
    public void senderProvidingPayloadAcceptsMissingSenderSignaturePubKeyWhenNotRequired() throws NoSuchAlgorithmException {
        SignaturePubKeyProvidingPayload payload = new SignaturePubKeyProvidingPayload(null, false);

        assertTrue(SendersSignaturePubKeyProvidingPayload.isSenderSignaturePubKeyMatching(payload.getSenderSignaturePubKey(),
                TestUtils.generateKeyPair().getPublic(),
                payload.isSenderSignaturePubKeyRequired()));
    }

    private static class SignaturePubKeyProvidingPayload extends NetworkEnvelope
            implements SendersSignaturePubKeyProvidingPayload {
        @Nullable
        private final PublicKey senderSignaturePubKey;
        private final boolean senderSignaturePubKeyRequired;

        private SignaturePubKeyProvidingPayload(@Nullable PublicKey senderSignaturePubKey) {
            this(senderSignaturePubKey, true);
        }

        private SignaturePubKeyProvidingPayload(@Nullable PublicKey senderSignaturePubKey,
                                                boolean senderSignaturePubKeyRequired) {
            super(0);
            this.senderSignaturePubKey = senderSignaturePubKey;
            this.senderSignaturePubKeyRequired = senderSignaturePubKeyRequired;
        }

        @Override
        @Nullable
        public PublicKey getSenderSignaturePubKey() {
            return senderSignaturePubKey;
        }

        @Override
        public boolean isSenderSignaturePubKeyRequired() {
            return senderSignaturePubKeyRequired;
        }

        @Override
        public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
            throw new NotImplementedException("toProtoNetworkEnvelope not impl.");
        }
    }
}
