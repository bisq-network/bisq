package bisq.core.support.dispute;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.support.SupportType;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DisputeValidationTest {
    private static final String TRADE_ID = "trade-id";
    private static final int TRADER_ID = 0;
    private static final NodeAddress BUYER_NODE_ADDRESS = new NodeAddress("buyer.onion", 9999);
    private static final NodeAddress SELLER_NODE_ADDRESS = new NodeAddress("seller.onion", 9999);
    private static final NodeAddress MEDIATOR_NODE_ADDRESS = new NodeAddress("mediator.onion", 9999);
    private static final NodeAddress REFUND_AGENT_NODE_ADDRESS = new NodeAddress("refund.onion", 9999);
    private static final long POST_ACTIVATION_TRADE_DATE =
            Contract.DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE.getTime() + 1;
    private static final Date POST_ACTIVATION_NOW =
            new Date(Contract.DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE.getTime() + 1);

    @Test
    void validateDisputeDataRejectsLegacyContractAfterActivationForPostActivationTrade() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing mediatorPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, null, null);
        Dispute dispute = dispute(buyerPubKeyRing, mediatorPubKeyRing, contract, SupportType.MEDIATION);

        assertThrows(DisputeValidation.ValidationException.class,
                () -> DisputeValidation.validateDisputeData(dispute, mock(BtcWalletService.class), POST_ACTIVATION_NOW));
    }

    @Test
    void validateDisputeDataAcceptsMatchingMediatorPubKeyFromNewContract() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing mediatorPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, mediatorPubKeyRing, pubKeyRing());
        Dispute dispute = dispute(buyerPubKeyRing, mediatorPubKeyRing, contract, SupportType.MEDIATION);

        assertDoesNotThrow(
                () -> DisputeValidation.validateDisputeData(dispute, mock(BtcWalletService.class), POST_ACTIVATION_NOW));
    }

    @Test
    void validateDisputeDataRejectsMismatchedMediatorPubKeyFromNewContract() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, pubKeyRing(), pubKeyRing());
        Dispute dispute = dispute(buyerPubKeyRing, pubKeyRing(), contract, SupportType.MEDIATION);

        assertThrows(DisputeValidation.ValidationException.class,
                () -> DisputeValidation.validateDisputeData(dispute, mock(BtcWalletService.class), POST_ACTIVATION_NOW));
    }

    private static Dispute dispute(PubKeyRing traderPubKeyRing,
                                   PubKeyRing agentPubKeyRing,
                                   Contract contract,
                                   SupportType supportType) {
        String contractAsJson = JsonUtil.objectToJson(contract);
        return new Dispute(
                0,
                TRADE_ID,
                TRADER_ID,
                true,
                true,
                traderPubKeyRing,
                POST_ACTIVATION_TRADE_DATE,
                0,
                contract,
                Hash.getSha256Hash(contractAsJson),
                null,
                null,
                null,
                null,
                contractAsJson,
                null,
                null,
                agentPubKeyRing,
                false,
                supportType);
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
        return new OfferPayload(TRADE_ID,
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
