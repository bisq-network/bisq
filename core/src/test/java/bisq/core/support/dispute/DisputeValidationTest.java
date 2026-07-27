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
    private static final long PRE_ACTIVATION_TRADE_DATE =
            Contract.DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE.getTime() - 1;
    private static final long POST_ACTIVATION_TRADE_DATE =
            Contract.DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE.getTime() + 1;
    private static final Date POST_ACTIVATION_NOW =
            new Date(Contract.DISPUTE_AGENT_PUB_KEYS_ACTIVATION_DATE.getTime() + 1);

    @Test
    void validateDisputeDataDoesNotUseSenderSuppliedTradeDateWithoutLocalTrade() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing mediatorPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, null, null);
        Dispute dispute = dispute(buyerPubKeyRing, mediatorPubKeyRing, contract, SupportType.MEDIATION);

        assertDoesNotThrow(
                () -> DisputeValidation.validateDisputeData(dispute, mock(BtcWalletService.class), POST_ACTIVATION_NOW));
    }

    @Test
    void validateDisputeDataRejectsLegacyContractAfterActivationForPostActivationLocalTrade() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, null, null);
        Dispute dispute = dispute(buyerPubKeyRing, pubKeyRing(), contract, SupportType.MEDIATION, PRE_ACTIVATION_TRADE_DATE);

        assertThrows(DisputeValidation.ValidationException.class,
                () -> DisputeValidation.validateDisputeData(dispute,
                        mock(BtcWalletService.class),
                        POST_ACTIVATION_NOW,
                        new Date(POST_ACTIVATION_TRADE_DATE)));
    }

    @Test
    void validateDisputeDataAcceptsLegacyContractForPreActivationLocalTrade() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, null, null);
        Dispute dispute = dispute(buyerPubKeyRing, pubKeyRing(), contract, SupportType.MEDIATION, PRE_ACTIVATION_TRADE_DATE);

        assertDoesNotThrow(
                () -> DisputeValidation.validateDisputeData(dispute,
                        mock(BtcWalletService.class),
                        POST_ACTIVATION_NOW,
                        new Date(PRE_ACTIVATION_TRADE_DATE)));
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

    @Test
    void validateDisputeDataAcceptsMatchingRefundAgentPubKeyFromNewContract() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing refundAgentPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, pubKeyRing(), refundAgentPubKeyRing);
        Dispute dispute = dispute(buyerPubKeyRing, refundAgentPubKeyRing, contract, SupportType.REFUND);

        assertDoesNotThrow(
                () -> DisputeValidation.validateDisputeData(dispute, mock(BtcWalletService.class), POST_ACTIVATION_NOW));
    }

    @Test
    void validateDisputeDataRejectsMismatchedRefundAgentPubKeyFromNewContract() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, pubKeyRing(), pubKeyRing());
        Dispute dispute = dispute(buyerPubKeyRing, pubKeyRing(), contract, SupportType.REFUND);

        assertThrows(DisputeValidation.ValidationException.class,
                () -> DisputeValidation.validateDisputeData(dispute, mock(BtcWalletService.class), POST_ACTIVATION_NOW));
    }

    @Test
    void validateDisputeDataDoesNotRequireContractBoundAgentKeyForLegacyArbitration() {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, pubKeyRing(), pubKeyRing());
        Dispute dispute = dispute(buyerPubKeyRing, pubKeyRing(), contract, SupportType.ARBITRATION);

        assertDoesNotThrow(
                () -> DisputeValidation.validateDisputeData(dispute, mock(BtcWalletService.class), POST_ACTIVATION_NOW));
    }

    @Test
    void replayCheckAcceptsFirstDisputeNotYetInList() {
        // Regression: the fail-closed ingest path validates before the dispute is added to the list. The replay
        // check must interpret the stored count together with the dispute under test, otherwise the first legitimate
        // dispute for a trade is rejected with a misleading "more then 2 disputes" error, breaking mediation.
        Dispute dispute = replayDispute("uid-1");

        assertDoesNotThrow(() -> DisputeValidation.testIfDisputeTriesReplay(dispute, List.of()));
    }

    @Test
    void replayCheckAcceptsSecondDisputeForSameTrade() {
        Dispute stored = replayDispute("uid-1");
        Dispute incoming = replayDispute("uid-2");

        assertDoesNotThrow(() -> DisputeValidation.testIfDisputeTriesReplay(incoming, List.of(stored)));
    }

    @Test
    void replayCheckAcceptsDisputeAlreadyInList() {
        Dispute dispute = replayDispute("uid-1");

        assertDoesNotThrow(() -> DisputeValidation.testIfDisputeTriesReplay(dispute, List.of(dispute)));
    }

    @Test
    void replayCheckRejectsThirdDisputeForSameTrade() {
        Dispute stored1 = replayDispute("uid-1");
        Dispute stored2 = replayDispute("uid-2");
        Dispute incoming = replayDispute("uid-3");

        assertThrows(DisputeValidation.DisputeReplayException.class,
                () -> DisputeValidation.testIfDisputeTriesReplay(incoming, List.of(stored1, stored2)));
    }

    private static Dispute replayDispute(String uid) {
        PubKeyRing buyerPubKeyRing = pubKeyRing();
        PubKeyRing sellerPubKeyRing = pubKeyRing();
        PubKeyRing mediatorPubKeyRing = pubKeyRing();
        Contract contract = contract(buyerPubKeyRing, sellerPubKeyRing, mediatorPubKeyRing, pubKeyRing());
        String contractAsJson = JsonUtil.objectToJson(contract);
        Dispute dispute = new Dispute(
                0,
                TRADE_ID,
                TRADER_ID,
                true,
                true,
                buyerPubKeyRing,
                POST_ACTIVATION_TRADE_DATE,
                0,
                contract,
                Hash.getSha256Hash(contractAsJson),
                null,
                null,
                "depositTxId",
                null,
                contractAsJson,
                null,
                null,
                mediatorPubKeyRing,
                false,
                SupportType.MEDIATION);
        dispute.setUid(uid);
        return dispute;
    }

    private static Dispute dispute(PubKeyRing traderPubKeyRing,
                                   PubKeyRing agentPubKeyRing,
                                   Contract contract,
                                   SupportType supportType) {
        return dispute(traderPubKeyRing, agentPubKeyRing, contract, supportType, POST_ACTIVATION_TRADE_DATE);
    }

    private static Dispute dispute(PubKeyRing traderPubKeyRing,
                                   PubKeyRing agentPubKeyRing,
                                   Contract contract,
                                   SupportType supportType,
                                   long tradeDate) {
        String contractAsJson = JsonUtil.objectToJson(contract);
        return new Dispute(
                0,
                TRADE_ID,
                TRADER_ID,
                true,
                true,
                traderPubKeyRing,
                tradeDate,
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
