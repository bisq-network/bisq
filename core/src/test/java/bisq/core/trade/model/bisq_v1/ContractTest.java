package bisq.core.trade.model.bisq_v1;

import bisq.core.offer.OfferDirection;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ContractTest {
    private static final NodeAddress BUYER_NODE_ADDRESS = new NodeAddress("buyer.onion", 9999);
    private static final NodeAddress SELLER_NODE_ADDRESS = new NodeAddress("seller.onion", 9999);
    private static final NodeAddress MEDIATOR_NODE_ADDRESS = new NodeAddress("mediator.onion", 9999);
    private static final NodeAddress REFUND_AGENT_NODE_ADDRESS = new NodeAddress("refund.onion", 9999);

    @Test
    void legacyContractJsonDoesNotIncludeVersionOrDisputeAgentPubKeys() {
        String json = JsonUtil.objectToJson(contract(pubKeyRing(), pubKeyRing(), null, null));

        assertFalse(json.contains("contractVersion"));
        assertFalse(json.contains("mediatorPubKeyRing"));
        assertFalse(json.contains("refundAgentPubKeyRing"));
    }

    @Test
    void newContractJsonIncludesVersionAndDisputeAgentPubKeys() {
        String json = JsonUtil.objectToJson(contract(pubKeyRing(), pubKeyRing(), pubKeyRing(), pubKeyRing()));

        assertTrue(json.contains("contractVersion"));
        assertTrue(json.contains("mediatorPubKeyRing"));
        assertTrue(json.contains("refundAgentPubKeyRing"));
    }

    @Test
    void fromProtoUsesNewVersionWhenDisputeAgentPubKeyFieldsArePresent() {
        Contract contract = contract(pubKeyRing(), pubKeyRing(), pubKeyRing(), pubKeyRing());
        protobuf.Contract protoWithoutVersion = contract.toProtoMessage().toBuilder()
                .clearContractVersion()
                .build();

        Contract fromProto = Contract.fromProto(protoWithoutVersion, mock(CoreProtoResolver.class));

        assertEquals(Contract.VERSION_WITH_DISPUTE_AGENT_PUB_KEYS, fromProto.getContractVersion());
        assertTrue(fromProto.hasDisputeAgentPubKeyVersion());
    }

    @Test
    void fromProtoRejectsNewVersionWithoutMediatorPubKeyRing() {
        Contract contract = contract(pubKeyRing(), pubKeyRing(), pubKeyRing(), pubKeyRing());
        protobuf.Contract protoWithoutMediator = contract.toProtoMessage().toBuilder()
                .clearMediatorPubKeyRing()
                .build();

        assertThrows(NullPointerException.class,
                () -> Contract.fromProto(protoWithoutMediator, mock(CoreProtoResolver.class)));
    }

    @Test
    void fromProtoRejectsNewVersionWithoutRefundAgentPubKeyRing() {
        Contract contract = contract(pubKeyRing(), pubKeyRing(), pubKeyRing(), pubKeyRing());
        protobuf.Contract protoWithoutRefundAgent = contract.toProtoMessage().toBuilder()
                .clearRefundAgentPubKeyRing()
                .build();

        assertThrows(NullPointerException.class,
                () -> Contract.fromProto(protoWithoutRefundAgent, mock(CoreProtoResolver.class)));
    }

    @Test
    void requiresDisputeAgentPubKeyVersionHonorsActivationAndTradeDate() {
        long activation = Contract.DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE.getTime();
        Date beforeActivation = new Date(activation - 1);
        Date atActivation = Contract.DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE;
        Date afterActivation = new Date(activation + 1);

        assertFalse(Contract.requiresDisputeAgentPubKeyVersion(beforeActivation, 0));
        assertFalse(Contract.requiresDisputeAgentPubKeyVersion(atActivation, activation));
        assertFalse(Contract.requiresDisputeAgentPubKeyVersion(afterActivation, activation - 1));
        assertTrue(Contract.requiresDisputeAgentPubKeyVersion(afterActivation, activation));
        assertTrue(Contract.requiresDisputeAgentPubKeyVersion(afterActivation, activation + 1));
        assertTrue(Contract.requiresDisputeAgentPubKeyVersion(afterActivation, 0));
    }

    private static Contract contract(PubKeyRing buyerPubKeyRing,
                                     PubKeyRing sellerPubKeyRing,
                                     PubKeyRing mediatorPubKeyRing,
                                     PubKeyRing refundAgentPubKeyRing) {
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
                REFUND_AGENT_NODE_ADDRESS,
                null,
                null,
                PaymentMethod.SEPA_ID,
                PaymentMethod.SEPA_ID,
                0,
                mediatorPubKeyRing,
                refundAgentPubKeyRing);
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
}
