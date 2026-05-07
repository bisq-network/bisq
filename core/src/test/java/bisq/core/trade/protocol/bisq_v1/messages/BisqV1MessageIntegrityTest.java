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

package bisq.core.trade.protocol.bisq_v1.messages;

import bisq.core.account.sign.SignedWitness;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.payment.payload.PaymentAccountPayload;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class BisqV1MessageIntegrityTest {
    private static final String TRADE_ID = "trade-id";
    private static final String UID = "uid";
    private static final NodeAddress NODE_ADDRESS = new NodeAddress("peer.onion", 9999);

    @Test
    void constructorsAcceptValidMessages() {
        assertDoesNotThrow(() -> new DepositTxMessage(UID, TRADE_ID, NODE_ADDRESS, bytes(1)));
        assertDoesNotThrow(() -> new CounterCurrencyTransferStartedMessage(TRADE_ID,
                "buyer-payout-address",
                NODE_ADDRESS,
                bytes(2),
                "counter-currency-tx-id",
                "counter-currency-extra-data",
                UID));
        assertDoesNotThrow(() -> new PayoutTxPublishedMessage(TRADE_ID, bytes(3), NODE_ADDRESS, null));
        assertDoesNotThrow(() -> new DelayedPayoutTxSignatureRequest(UID, TRADE_ID, NODE_ADDRESS, bytes(4), bytes(5)));
        assertDoesNotThrow(() -> new ShareBuyerPaymentAccountMessage(UID,
                TRADE_ID,
                NODE_ADDRESS,
                mock(PaymentAccountPayload.class)));
        assertDoesNotThrow(() -> new DelayedPayoutTxSignatureResponse(UID, TRADE_ID, NODE_ADDRESS, bytes(6), bytes(7)));
        assertDoesNotThrow(() -> new DepositTxAndDelayedPayoutTxMessage(UID,
                TRADE_ID,
                NODE_ADDRESS,
                bytes(8),
                bytes(9),
                null));
        assertDoesNotThrow(() -> new MediatedPayoutTxPublishedMessage(TRADE_ID, bytes(10), NODE_ADDRESS, UID));
        assertDoesNotThrow(() -> new PeerPublishedDelayedPayoutTxMessage(UID, TRADE_ID, NODE_ADDRESS));
        assertDoesNotThrow(() -> new MediatedPayoutTxSignatureMessage(bytes(11), TRADE_ID, NODE_ADDRESS, UID));
        assertDoesNotThrow(() -> newResponse(args -> {
        }));
        assertDoesNotThrow(() -> newRequest(args -> {
        }));
    }

    @Test
    void constructorsRejectMissingTradeIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new DepositTxMessage(UID, "", NODE_ADDRESS, bytes(1)));
        assertThrows(IllegalArgumentException.class, () -> new DelayedPayoutTxSignatureRequest("",
                TRADE_ID,
                NODE_ADDRESS,
                bytes(2),
                bytes(3)));
        assertThrows(IllegalArgumentException.class, () -> new DelayedPayoutTxSignatureResponse(UID,
                "",
                NODE_ADDRESS,
                bytes(4),
                bytes(5)));
        assertThrows(IllegalArgumentException.class, () -> new CounterCurrencyTransferStartedMessage(TRADE_ID,
                "buyer-payout-address",
                NODE_ADDRESS,
                bytes(6),
                null,
                null,
                ""));
        assertThrows(IllegalArgumentException.class, () -> newResponse(args -> args.uid = ""));
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.tradeId = ""));
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.uid = ""));
    }

    @Test
    void constructorsRejectNullRequiredReferences() {
        assertThrows(NullPointerException.class, () -> new DepositTxMessage(UID, TRADE_ID, null, bytes(1)));
        assertThrows(NullPointerException.class, () -> new CounterCurrencyTransferStartedMessage(TRADE_ID,
                "buyer-payout-address",
                null,
                bytes(2),
                null,
                null,
                UID));
        assertThrows(NullPointerException.class, () -> new PayoutTxPublishedMessage(TRADE_ID, bytes(3), null, null));
        assertThrows(NullPointerException.class, () -> new DelayedPayoutTxSignatureRequest(UID,
                TRADE_ID,
                null,
                bytes(4),
                bytes(5)));
        assertThrows(NullPointerException.class, () -> new ShareBuyerPaymentAccountMessage(UID,
                TRADE_ID,
                NODE_ADDRESS,
                null));
        assertThrows(NullPointerException.class, () -> new DelayedPayoutTxSignatureResponse(UID,
                TRADE_ID,
                null,
                bytes(6),
                bytes(7)));
        assertThrows(NullPointerException.class, () -> new DepositTxAndDelayedPayoutTxMessage(UID,
                TRADE_ID,
                null,
                bytes(8),
                bytes(9),
                null));
        assertThrows(NullPointerException.class, () -> new MediatedPayoutTxPublishedMessage(TRADE_ID,
                bytes(10),
                null,
                UID));
        assertThrows(NullPointerException.class, () -> new PeerPublishedDelayedPayoutTxMessage(UID,
                TRADE_ID,
                null));
        assertThrows(NullPointerException.class, () -> new MediatedPayoutTxSignatureMessage(bytes(11),
                TRADE_ID,
                null,
                UID));
        assertThrows(NullPointerException.class, () -> newResponse(args -> args.senderNodeAddress = null));
        assertThrows(NullPointerException.class, () -> newRequest(args -> args.senderNodeAddress = null));
    }

    @Test
    void constructorsRejectEmptyRequiredBytes() {
        assertThrows(IllegalArgumentException.class, () -> new DepositTxMessage(UID,
                TRADE_ID,
                NODE_ADDRESS,
                new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new CounterCurrencyTransferStartedMessage(TRADE_ID,
                "buyer-payout-address",
                NODE_ADDRESS,
                new byte[0],
                null,
                null,
                UID));
        assertThrows(IllegalArgumentException.class, () -> new PayoutTxPublishedMessage(TRADE_ID,
                new byte[0],
                NODE_ADDRESS,
                null));
        assertThrows(IllegalArgumentException.class, () -> new DelayedPayoutTxSignatureRequest(UID,
                TRADE_ID,
                NODE_ADDRESS,
                new byte[0],
                bytes(1)));
        assertThrows(IllegalArgumentException.class, () -> new DelayedPayoutTxSignatureResponse(UID,
                TRADE_ID,
                NODE_ADDRESS,
                bytes(2),
                new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new DepositTxAndDelayedPayoutTxMessage(UID,
                TRADE_ID,
                NODE_ADDRESS,
                bytes(3),
                new byte[0],
                null));
        assertThrows(IllegalArgumentException.class, () -> new MediatedPayoutTxPublishedMessage(TRADE_ID,
                new byte[0],
                NODE_ADDRESS,
                UID));
        assertThrows(IllegalArgumentException.class, () -> new MediatedPayoutTxSignatureMessage(new byte[0],
                TRADE_ID,
                NODE_ADDRESS,
                UID));
        assertThrows(IllegalArgumentException.class, () -> newResponse(args -> args.preparedDepositTx = new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.takerMultiSigPubKey = new byte[0]));
    }

    @Test
    void constructorsRejectEmptyMessageStrings() {
        assertThrows(IllegalArgumentException.class, () -> new CounterCurrencyTransferStartedMessage(TRADE_ID,
                "",
                NODE_ADDRESS,
                bytes(1),
                null,
                null,
                UID));
        assertThrows(IllegalArgumentException.class, () -> newResponse(args -> args.makerAccountId = ""));
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.takerAccountId = ""));
    }

    @Test
    void constructorsRejectEmptyOptionalValuesWhenPresent() {
        assertThrows(IllegalArgumentException.class, () -> new CounterCurrencyTransferStartedMessage(TRADE_ID,
                "buyer-payout-address",
                NODE_ADDRESS,
                bytes(1),
                "",
                null,
                UID));
        assertThrows(IllegalArgumentException.class, () -> newResponse(args -> args.makersPaymentMethodId = ""));
        assertThrows(IllegalArgumentException.class,
                () -> newResponse(args -> args.hashOfMakersPaymentAccountPayload = new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.takersPaymentMethodId = ""));
        assertThrows(IllegalArgumentException.class,
                () -> newRequest(args -> args.hashOfTakersPaymentAccountPayload = new byte[0]));
    }

    @Test
    void inputsForDepositTxResponseRejectsInvalidMakerInputs() {
        assertThrows(IllegalArgumentException.class, () -> newResponse(args -> args.makerInputs = List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> newResponse(args -> args.makerInputs = Arrays.asList(rawTransactionInput(), null)));
    }

    @Test
    void inputsForDepositTxRequestRejectsInvalidDate() {
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.currentDate = 0));
    }

    @Test
    void inputsForDepositTxResponseRejectsInvalidDateAndLockTime() {
        assertThrows(IllegalArgumentException.class, () -> newResponse(args -> args.currentDate = 0));
        assertThrows(IllegalArgumentException.class, () -> newResponse(args -> args.lockTime = 0));
    }

    @Test
    void inputsForDepositTxRequestRejectsInvalidInputLists() {
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.rawTransactionInputs = List.of()));
        assertThrows(IllegalArgumentException.class, () -> newRequest(args -> args.acceptedMediatorNodeAddresses =
                Arrays.asList(args.mediatorNodeAddress, null)));
    }

    private static InputsForDepositTxResponse newResponse(Consumer<ResponseArgs> customizer) {
        ResponseArgs args = new ResponseArgs();
        customizer.accept(args);
        return new InputsForDepositTxResponse(
                args.tradeId,
                args.makerAccountId,
                args.makerMultiSigPubKey,
                args.makerContractAsJson,
                args.makerContractSignature,
                args.makerPayoutAddressString,
                args.preparedDepositTx,
                args.makerInputs,
                args.senderNodeAddress,
                args.uid,
                args.accountAgeWitnessSignatureOfPreparedDepositTx,
                args.currentDate,
                args.lockTime,
                args.hashOfMakersPaymentAccountPayload,
                args.makersPaymentMethodId);
    }

    private static InputsForDepositTxRequest newRequest(Consumer<RequestArgs> customizer) {
        RequestArgs args = new RequestArgs();
        customizer.accept(args);
        return new InputsForDepositTxRequest(
                args.tradeId,
                args.senderNodeAddress,
                args.tradeAmount,
                args.tradePrice,
                args.txFee,
                args.takerFee,
                args.isCurrencyForTakerFeeBtc,
                args.rawTransactionInputs,
                args.takerMultiSigPubKey,
                args.takerPayoutAddressString,
                args.takerPubKeyRing,
                args.takerAccountId,
                args.takerFeeTxId,
                args.acceptedArbitratorNodeAddresses,
                args.acceptedMediatorNodeAddresses,
                args.acceptedRefundAgentNodeAddresses,
                args.arbitratorNodeAddress,
                args.mediatorNodeAddress,
                args.refundAgentNodeAddress,
                args.uid,
                args.messageVersion,
                args.accountAgeWitnessSignatureOfOfferId,
                args.currentDate,
                args.hashOfTakersPaymentAccountPayload,
                args.takersPaymentMethodId,
                args.burningManSelectionHeight);
    }

    private static protobuf.RefreshTradeStateRequest refreshTradeStateRequest(String tradeId, String uid) {
        return protobuf.RefreshTradeStateRequest.newBuilder()
                .setTradeId(tradeId)
                .setUid(uid)
                .setSenderNodeAddress(NODE_ADDRESS.toProtoMessage())
                .build();
    }

    private static SignedWitness signedWitness() {
        return new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                bytes(12),
                bytes(13),
                bytes(14),
                bytes(15),
                1L,
                1L);
    }

    private static RawTransactionInput rawTransactionInput() {
        return new RawTransactionInput(0, bytes(16), 100_000L);
    }

    private static byte[] bytes(int value) {
        return new byte[]{(byte) value};
    }

    private static class ResponseArgs {
        private String tradeId = TRADE_ID;
        private String makerAccountId = "maker-account-id";
        private byte[] makerMultiSigPubKey = bytes(17);
        private String makerContractAsJson = "{}";
        private String makerContractSignature = "maker-contract-signature";
        private String makerPayoutAddressString = "maker-payout-address";
        private byte[] preparedDepositTx = bytes(18);
        private List<RawTransactionInput> makerInputs = List.of(rawTransactionInput());
        private NodeAddress senderNodeAddress = NODE_ADDRESS;
        private String uid = UID;
        private byte[] accountAgeWitnessSignatureOfPreparedDepositTx = bytes(19);
        private long currentDate = 1L;
        private long lockTime = 1L;
        private byte[] hashOfMakersPaymentAccountPayload = bytes(20);
        private String makersPaymentMethodId = "SEPA";
    }

    private static class RequestArgs {
        private String tradeId = TRADE_ID;
        private NodeAddress senderNodeAddress = new NodeAddress("sender.onion", 9999);
        private long tradeAmount = 100_000L;
        private long tradePrice = 50_000_000L;
        private long txFee = 10_000L;
        private long takerFee = 1_000L;
        private boolean isCurrencyForTakerFeeBtc = false;
        private List<RawTransactionInput> rawTransactionInputs = List.of(
                new RawTransactionInput(0, bytes(21), 100_000L));
        private byte[] takerMultiSigPubKey = bytes(22);
        private String takerPayoutAddressString = "taker-payout-address";
        private PubKeyRing takerPubKeyRing = mock(PubKeyRing.class);
        private String takerAccountId = "taker-account-id";
        private String takerFeeTxId = "taker-fee-tx-id";
        private List<NodeAddress> acceptedArbitratorNodeAddresses = List.of();
        private List<NodeAddress> acceptedMediatorNodeAddresses = List.of(new NodeAddress("mediator.onion", 9999));
        private List<NodeAddress> acceptedRefundAgentNodeAddresses = List.of();
        private NodeAddress arbitratorNodeAddress = null;
        private NodeAddress mediatorNodeAddress = new NodeAddress("mediator.onion", 9999);
        private NodeAddress refundAgentNodeAddress = new NodeAddress("refund.onion", 9999);
        private String uid = UID;
        private int messageVersion = 1;
        private byte[] accountAgeWitnessSignatureOfOfferId = bytes(23);
        private long currentDate = 1L;
        private byte[] hashOfTakersPaymentAccountPayload = bytes(24);
        private String takersPaymentMethodId = "SEPA";
        private int burningManSelectionHeight = 1;
    }
}
