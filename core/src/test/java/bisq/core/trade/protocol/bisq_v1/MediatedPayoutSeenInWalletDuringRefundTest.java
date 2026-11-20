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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.utils.PayoutTransactionUtil;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Optional;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A refund request means that the delayed payout tx is published, which spends the deposit output of the mediated
 * payout. A mediated payout that the wallet sees then counts only once it is confirmed, as for the peer's
 * MediatedPayoutTxPublishedMessage. Real trade, real DisputeProtocol startup, real listener task and real payout
 * validation on a signed 2-of-2 payout; only the wallet services are mocked.
 */
class MediatedPayoutSeenInWalletDuringRefundTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();
    private static final NodeAddress PEER = new NodeAddress("peer.onion:9999");
    private static final Coin DEPOSIT_OUTPUT_AMOUNT = Coin.valueOf(120_000);
    private static final Coin BUYER_PAYOUT_AMOUNT = Coin.valueOf(80_000);
    private static final Coin SELLER_PAYOUT_AMOUNT = Coin.valueOf(30_000);
    private static final ECKey BUYER_MULTI_SIG_KEY = new ECKey();
    private static final ECKey SELLER_MULTI_SIG_KEY = new ECKey();
    private static final Address BUYER_PAYOUT_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey());
    private static final Address SELLER_PAYOUT_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey());
    private static final Script REDEEM_SCRIPT = PayoutTransactionUtil.get2of2MultiSigRedeemScript(
            BUYER_MULTI_SIG_KEY.getPubKey(), SELLER_MULTI_SIG_KEY.getPubKey());

    private final Provider provider = mock(Provider.class);
    private final BtcWalletService btcWalletService = mock(BtcWalletService.class);
    private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
    private Transaction depositTx;
    private Transaction mediatedPayoutTx;

    @BeforeEach
    void setUp() {
        Context.propagate(new Context(PARAMS));
        UserThread.setTimerClass(ManualTimer.class);
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        when(provider.getP2PService()).thenReturn(p2PService);
        when(provider.getUser()).thenReturn(mock(User.class));
        when(provider.getBtcWalletService()).thenReturn(btcWalletService);
        when(provider.getTradeWalletService()).thenReturn(tradeWalletService);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        AddressEntry payoutAddressEntry = mock(AddressEntry.class);
        when(payoutAddressEntry.getAddress()).thenReturn(SELLER_PAYOUT_ADDRESS);
        when(payoutAddressEntry.getAddressString()).thenReturn(SELLER_PAYOUT_ADDRESS.toString());
        when(btcWalletService.getAddressEntry(anyString(), eq(AddressEntry.Context.TRADE_PAYOUT)))
                .thenReturn(Optional.of(payoutAddressEntry));
        when(btcWalletService.getOrCreateAddressEntry(anyString(), eq(AddressEntry.Context.TRADE_PAYOUT)))
                .thenReturn(payoutAddressEntry);

        depositTx = createDepositTx();
        // The deposit is confirmed, as in every trade past DEPOSIT_CONFIRMED
        depositTx.getConfidence().setAppearedAtChainHeight(100);
        mediatedPayoutTx = createSignedPayoutTx(depositTx, BUYER_PAYOUT_AMOUNT, SELLER_PAYOUT_AMOUNT);
        when(tradeWalletService.getWalletTx(mediatedPayoutTx.getTxId())).thenReturn(mediatedPayoutTx);
    }

    @AfterEach
    void tearDown() {
        UserThread.setTimerClass(FrameRateTimer.class);
        ManualTimer.clear();
    }

    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void unconfirmedPayoutDuringARefundRequestIsIgnoredAndStillWatched(Trade.DisputeState disputeState) {
        Trade trade = trade(disputeState);
        new DisputeProtocol(trade).onInitialized();

        capturedListener().onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.PENDING, mediatedPayoutTx.getTxId()));

        assertNull(trade.getPayoutTx());
        verify(trade.getProcessModel().getTradeManager(), never()).closeDisputedTrade(anyString(), any());
        verify(btcWalletService, never()).removeAddressConfidenceListener(any());
    }

    @Test
    void unconfirmedPayoutDuringARefundRequestCompletesTheTradeOnceConfirmed() {
        Trade trade = trade(Trade.DisputeState.REFUND_REQUEST_STARTED_BY_PEER);
        new DisputeProtocol(trade).onInitialized();
        AddressConfidenceListener listener = capturedListener();
        listener.onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.PENDING, mediatedPayoutTx.getTxId()));
        assertNull(trade.getPayoutTx());

        listener.onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.BUILDING, mediatedPayoutTx.getTxId()));

        assertSame(mediatedPayoutTx, trade.getPayoutTx());
        verify(trade.getProcessModel().getTradeManager())
                .closeDisputedTrade(trade.getId(), Trade.DisputeState.MEDIATION_CLOSED);
    }

    // At startup the wallet can already have the payout
    @Test
    void unconfirmedPayoutInTheWalletAtStartupDuringARefundRequestIsIgnoredAndStillWatched() {
        Trade trade = trade(Trade.DisputeState.REFUND_REQUEST_STARTED_BY_PEER);
        TransactionConfidence pending = confidence(TransactionConfidence.ConfidenceType.PENDING,
                mediatedPayoutTx.getTxId());
        when(btcWalletService.getConfidenceForAddressFromBlockHeight(eq(SELLER_PAYOUT_ADDRESS), anyLong()))
                .thenReturn(pending);

        new DisputeProtocol(trade).onInitialized();

        assertNull(trade.getPayoutTx());
        verify(trade.getProcessModel().getTradeManager(), never()).closeDisputedTrade(anyString(), any());
        assertEquals(SELLER_PAYOUT_ADDRESS, capturedListener().getAddress());
    }

    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void confirmedPayoutDuringARefundRequestCompletesTheTrade(Trade.DisputeState disputeState) {
        Trade trade = trade(disputeState);
        new DisputeProtocol(trade).onInitialized();

        capturedListener().onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.BUILDING, mediatedPayoutTx.getTxId()));

        assertSame(mediatedPayoutTx, trade.getPayoutTx());
        assertEquals(MediationResultState.PAYOUT_TX_SEEN_IN_NETWORK, trade.getMediationResultState());
        verify(trade.getProcessModel().getTradeManager())
                .closeDisputedTrade(trade.getId(), Trade.DisputeState.MEDIATION_CLOSED);
    }

    @Test
    void unconfirmedPayoutInMediationCompletesTheTrade() {
        Trade trade = trade(Trade.DisputeState.MEDIATION_CLOSED);
        new DisputeProtocol(trade).onInitialized();

        capturedListener().onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.PENDING, mediatedPayoutTx.getTxId()));

        assertSame(mediatedPayoutTx, trade.getPayoutTx());
        verify(trade.getProcessModel().getTradeManager())
                .closeDisputedTrade(trade.getId(), Trade.DisputeState.MEDIATION_CLOSED);
    }

    // Arbitration includes the legacy arbitration, which has no mediated payout
    @ParameterizedTest
    @EnumSource(Trade.DisputeState.class)
    void onlyArbitrationRequiresAConfirmedMediatedPayout(Trade.DisputeState disputeState) {
        assertTrue(disputeState.acceptsMediatedPayout(() -> true));
        assertEquals(!disputeState.isArbitrated(), disputeState.acceptsMediatedPayout(() -> false));
    }

    private AddressConfidenceListener capturedListener() {
        ArgumentCaptor<AddressConfidenceListener> captor = ArgumentCaptor.forClass(AddressConfidenceListener.class);
        verify(btcWalletService).addAddressConfidenceListener(captor.capture());
        return captor.getValue();
    }

    private static TransactionConfidence confidence(TransactionConfidence.ConfidenceType type, Sha256Hash txId) {
        TransactionConfidence confidence = mock(TransactionConfidence.class);
        when(confidence.getConfidenceType()).thenReturn(type);
        when(confidence.getTransactionHash()).thenReturn(txId);
        return confidence;
    }

    // Seller as maker in FIAT_SENT who has accepted the mediation result (own signature), with the contract, the
    // deposit tx and the mediation amounts the listener needs to check a payout
    private Trade trade(Trade.DisputeState disputeState) {
        Trade trade = MediationTestTrades.sellerAsMakerTrade(provider,
                Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG, disputeState);
        ProcessModel processModel = trade.getProcessModel();
        processModel.setMediatedPayoutTxSignature(new byte[]{1});
        processModel.setMyMultiSigPubKey(SELLER_MULTI_SIG_KEY.getPubKey());
        processModel.setBuyerPayoutAmountFromMediation(BUYER_PAYOUT_AMOUNT.value);
        processModel.setSellerPayoutAmountFromMediation(SELLER_PAYOUT_AMOUNT.value);
        TradingPeer tradingPeer = processModel.getTradePeer();
        tradingPeer.setMultiSigPubKey(BUYER_MULTI_SIG_KEY.getPubKey());
        tradingPeer.setPayoutAddressString(BUYER_PAYOUT_ADDRESS.toString());
        trade.setContract(contract(trade, processModel.getPubKeyRing()));
        trade.applyDepositTx(depositTx);
        // applyDepositTx marks the deposit as confirmed; keep the trade in FIAT_SENT with the refund dispute state
        trade.setState(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG);
        trade.setDisputeState(disputeState);
        return trade;
    }

    private static Contract contract(Trade trade, PubKeyRing myPubKeyRing) {
        PubKeyRing peerPubKeyRing = new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
        return new Contract(trade.getOffer().getOfferPayload().orElseThrow(),
                1_000_000L,
                50_000_000L,
                "takerFeeTxId",
                PEER,
                new NodeAddress("seller.onion:9999"),
                new NodeAddress("mediator.onion:9999"),
                false /* isBuyerMakerAndSellerTaker: we are the seller and the maker */,
                "makerAccountId",
                "takerAccountId",
                null,
                null,
                myPubKeyRing,
                peerPubKeyRing,
                SELLER_PAYOUT_ADDRESS.toString(),
                BUYER_PAYOUT_ADDRESS.toString(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                144,
                new NodeAddress("refund.onion:9999"),
                null,
                null,
                PaymentMethod.SEPA_ID,
                PaymentMethod.SEPA_ID,
                0);
    }

    private static Transaction createDepositTx() {
        Transaction depositTx = new Transaction(PARAMS);
        depositTx.addInput(new TransactionInput(PARAMS,
                depositTx,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.of(new byte[]{7})),
                DEPOSIT_OUTPUT_AMOUNT));
        depositTx.addOutput(DEPOSIT_OUTPUT_AMOUNT, ScriptBuilder.createP2WSHOutputScript(REDEEM_SCRIPT));
        return depositTx;
    }

    // Both traders' signatures, SIGHASH_ALL, in the witness form a BISQ wallet produces
    private static Transaction createSignedPayoutTx(Transaction depositTx,
                                                    Coin buyerPayoutAmount,
                                                    Coin sellerPayoutAmount) {
        Transaction payoutTx = new Transaction(PARAMS);
        payoutTx.addInput(depositTx.getOutput(0));
        payoutTx.addOutput(buyerPayoutAmount, BUYER_PAYOUT_ADDRESS);
        payoutTx.addOutput(sellerPayoutAmount, SELLER_PAYOUT_ADDRESS);
        Sha256Hash sigHash = payoutTx.hashForWitnessSignature(0,
                REDEEM_SCRIPT,
                depositTx.getOutput(0).getValue(),
                Transaction.SigHash.ALL,
                false);
        TransactionSignature sellerSignature = new TransactionSignature(SELLER_MULTI_SIG_KEY.sign(sigHash),
                Transaction.SigHash.ALL, false);
        TransactionSignature buyerSignature = new TransactionSignature(BUYER_MULTI_SIG_KEY.sign(sigHash),
                Transaction.SigHash.ALL, false);
        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(ScriptBuilder.createEmpty());
        input.setWitness(TransactionWitness.redeemP2WSH(REDEEM_SCRIPT, sellerSignature, buyerSignature));
        return payoutTx;
    }
}
