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

package bisq.core.offer.availability;

import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.offer.availability.messages.OfferAvailabilityResponse;

import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import java.security.PublicKey;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferAvailabilityProtocolResponseSenderTest {
    private final NodeAddress makerAddress = new NodeAddress("maker.onion:9999");
    private final PublicKey makerSignatureKey = Sig.generateKeyPair().getPublic();
    private OfferAvailabilityModel model;
    private OfferAvailabilityProtocol protocol;
    private DecryptedDirectMessageListener listener;

    @BeforeEach
    void setUp() {
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn("offerId");
        when(offer.getPubKeyRing()).thenReturn(new PubKeyRing(makerSignatureKey, Sig.generateKeyPair().getPublic()));
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getNetworkNode()).thenReturn(mock(NetworkNode.class));
        model = mock(OfferAvailabilityModel.class);
        when(model.getOffer()).thenReturn(offer);
        when(model.getP2PService()).thenReturn(p2PService);
        when(model.getPeerNodeAddress()).thenReturn(makerAddress);
        when(model.getDelayedPayoutTxReceiverService()).thenReturn(mock(DelayedPayoutTxReceiverService.class));

        protocol = new OfferAvailabilityProtocol(model, () -> {
        }, errorMessage -> {
        });
        protocol.sendOfferAvailabilityRequest();
        ArgumentCaptor<DecryptedDirectMessageListener> captor =
                ArgumentCaptor.forClass(DecryptedDirectMessageListener.class);
        verify(p2PService).addDecryptedDirectMessageListener(captor.capture());
        listener = captor.getValue();
    }

    // Stops the timeout of the request
    @AfterEach
    void tearDown() {
        protocol.cancel();
    }

    // The sender chooses the address of an inbound connection, so an answer that claims the maker's address
    // but is signed with another key, such as a forged USER_IGNORED, is not the maker's answer
    @Test
    void anAnswerSignedWithAnotherKeyIsIgnored() {
        OfferAvailabilityResponse response = response();

        listener.onDirectMessage(new DecryptedMessageWithPubKey(response, Sig.generateKeyPair().getPublic()),
                makerAddress);

        verify(model, never()).setMessage(any());
    }

    @Test
    void anAnswerSignedWithTheKeyOfTheOfferIsProcessed() {
        OfferAvailabilityResponse response = response();

        listener.onDirectMessage(new DecryptedMessageWithPubKey(response, makerSignatureKey), makerAddress);

        verify(model).setMessage(response);
    }

    private static OfferAvailabilityResponse response() {
        return new OfferAvailabilityResponse("offerId", AvailabilityResult.USER_IGNORED, null, null, null);
    }
}
