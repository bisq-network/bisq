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

package bisq.core.support.dispute.messages;

import bisq.core.offer.OfferDirection;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import java.security.PublicKey;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

public class DisputeOpenMessageTest {
    private static final String TRADE_ID = "trade-id";
    private static final int TRADER_ID = 0;
    private static final NodeAddress SENDER_NODE_ADDRESS = new NodeAddress("sender.onion", 9999);
    private static final NodeAddress BUYER_NODE_ADDRESS = new NodeAddress("buyer.onion", 9999);
    private static final NodeAddress SELLER_NODE_ADDRESS = new NodeAddress("seller.onion", 9999);
    private static final NodeAddress MEDIATOR_NODE_ADDRESS = new NodeAddress("mediator.onion", 9999);

    @Test
    public void openNewDisputeMessageRoundTripPreservesSenderSignaturePubKey() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute(buyerPubKeyRing, pubKeyRing(), pubKeyRing()),
                SENDER_NODE_ADDRESS,
                "uid",
                SupportType.MEDIATION);

        protobuf.OpenNewDisputeMessage proto = message.toProtoNetworkEnvelope().getOpenNewDisputeMessage();
        OpenNewDisputeMessage fromProto = OpenNewDisputeMessage.fromProto(proto, mock(CoreProtoResolver.class), 1);

        assertFalse(proto.getSenderSignaturePubKey().isEmpty());
        assertPublicKeysEqual(buyerPubKeyRing.getSignaturePubKey(), fromProto.getSenderSignaturePubKey());
    }

    @Test
    public void openNewDisputeMessageFromProtoKeepsMissingSenderSignaturePubKeyAsNull() {
        OpenNewDisputeMessage message = new OpenNewDisputeMessage(dispute(pubKeyRing(), pubKeyRing(), pubKeyRing()),
                SENDER_NODE_ADDRESS,
                "uid",
                SupportType.MEDIATION);
        protobuf.OpenNewDisputeMessage proto = message.toProtoNetworkEnvelope()
                .getOpenNewDisputeMessage()
                .toBuilder()
                .clearSenderSignaturePubKey()
                .build();

        OpenNewDisputeMessage fromProto = OpenNewDisputeMessage.fromProto(proto, mock(CoreProtoResolver.class), 1);

        assertNull(fromProto.getSenderSignaturePubKey());
    }

    @Test
    public void peerOpenedDisputeMessageRoundTripPreservesSenderSignaturePubKey() {
        PubKeyRing agentPubKeyRing = pubKeyRing();
        PeerOpenedDisputeMessage message = new PeerOpenedDisputeMessage(dispute(pubKeyRing(), pubKeyRing(), agentPubKeyRing),
                SENDER_NODE_ADDRESS,
                "uid",
                SupportType.MEDIATION);

        protobuf.PeerOpenedDisputeMessage proto = message.toProtoNetworkEnvelope().getPeerOpenedDisputeMessage();
        PeerOpenedDisputeMessage fromProto = PeerOpenedDisputeMessage.fromProto(proto, mock(CoreProtoResolver.class), 1);

        assertFalse(proto.getSenderSignaturePubKey().isEmpty());
        assertPublicKeysEqual(agentPubKeyRing.getSignaturePubKey(), fromProto.getSenderSignaturePubKey());
    }

    @Test
    public void peerOpenedDisputeMessageFromProtoKeepsMissingSenderSignaturePubKeyAsNull() {
        PeerOpenedDisputeMessage message = new PeerOpenedDisputeMessage(dispute(pubKeyRing(), pubKeyRing(), pubKeyRing()),
                SENDER_NODE_ADDRESS,
                "uid",
                SupportType.MEDIATION);
        protobuf.PeerOpenedDisputeMessage proto = message.toProtoNetworkEnvelope()
                .getPeerOpenedDisputeMessage()
                .toBuilder()
                .clearSenderSignaturePubKey()
                .build();

        PeerOpenedDisputeMessage fromProto = PeerOpenedDisputeMessage.fromProto(proto, mock(CoreProtoResolver.class), 1);

        assertNull(fromProto.getSenderSignaturePubKey());
    }

    private static Dispute dispute(PubKeyRing buyerPubKeyRing,
                                   PubKeyRing sellerPubKeyRing,
                                   PubKeyRing agentPubKeyRing) {
        return new Dispute(
                0,
                TRADE_ID,
                TRADER_ID,
                true,
                true,
                buyerPubKeyRing,
                DisputeMessage.SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE.getTime() + 1,
                0,
                contract(buyerPubKeyRing, sellerPubKeyRing),
                null,
                null,
                null,
                null,
                null,
                "",
                null,
                null,
                agentPubKeyRing,
                false,
                SupportType.MEDIATION);
    }

    private static Contract contract(PubKeyRing buyerPubKeyRing, PubKeyRing sellerPubKeyRing) {
        return new Contract(
                offerPayload(buyerPubKeyRing),
                1_000_000L,
                50_000_000L,
                "takerFeeTxId",
                BUYER_NODE_ADDRESS,
                SELLER_NODE_ADDRESS,
                MEDIATOR_NODE_ADDRESS,
                true,
                "makerAccountId",
                "takerAccountId",
                null,
                null,
                buyerPubKeyRing,
                sellerPubKeyRing,
                "makerPayoutAddress",
                "takerPayoutAddress",
                new byte[33],
                new byte[33],
                0,
                MEDIATOR_NODE_ADDRESS,
                null,
                null,
                PaymentMethod.SEPA_ID,
                PaymentMethod.SEPA_ID,
                0);
    }

    private static OfferPayload offerPayload(PubKeyRing makerPubKeyRing) {
        return new OfferPayload("offer-id",
                1_700_000_000_000L,
                BUYER_NODE_ADDRESS,
                makerPubKeyRing,
                OfferDirection.SELL,
                50_000_000L,
                0,
                false,
                1_000_000L,
                1_000_000L,
                "BTC",
                "EUR",
                List.of(),
                List.of(MEDIATOR_NODE_ADDRESS),
                PaymentMethod.SEPA_ID,
                "makerPaymentAccountId",
                "offerFeePaymentTxId",
                null,
                null,
                null,
                null,
                "1.9.9",
                123_456L,
                1_000L,
                2_000L,
                true,
                3_000L,
                4_000L,
                5_000L,
                6_000L,
                false,
                false,
                0,
                0,
                false,
                null,
                null,
                4);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }

    private static void assertPublicKeysEqual(PublicKey expected, PublicKey actual) {
        assertNotNull(actual);
        assertArrayEquals(Sig.getPublicKeyBytes(expected), Sig.getPublicKeyBytes(actual));
    }
}
