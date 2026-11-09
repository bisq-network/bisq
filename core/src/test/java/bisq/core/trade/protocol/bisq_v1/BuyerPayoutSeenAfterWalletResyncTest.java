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
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.testutil.ManualTimer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
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
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Optional;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * After a resync of the wallet, the deposit tx is only in the wallet once the wallet has synced its block. The buyer
 * watches the payout address at startup also before that, and completes the trade with the payout once the deposit tx
 * is known (GH #8179). Real trade, real BuyerProtocol startup, real listener task and real payout validation; only the
 * wallet services are mocked.
 */
class BuyerPayoutSeenAfterWalletResyncTest {
    private static final NetworkParameters PARAMS = RegTestParams.get();
    private static final Coin TRADE_AMOUNT = Coin.valueOf(100_000);
    private static final Coin SELLER_SECURITY_DEPOSIT = Coin.valueOf(15_000);
    private static final ECKey BUYER_MULTI_SIG_KEY = new ECKey();
    private static final ECKey SELLER_MULTI_SIG_KEY = new ECKey();
    private static final Address BUYER_PAYOUT_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey());
    private static final Address SELLER_PAYOUT_ADDRESS = SegwitAddress.fromKey(PARAMS, new ECKey());
    private static final Script REDEEM_SCRIPT = PayoutTransactionUtil.get2of2MultiSigRedeemScript(
            BUYER_MULTI_SIG_KEY.getPubKey(), SELLER_MULTI_SIG_KEY.getPubKey());

    private final Provider provider = mock(Provider.class);
    private final BtcWalletService btcWalletService = mock(BtcWalletService.class);
    private final TradeWalletService tradeWalletService = mock(TradeWalletService.class);
    private Offer offer;
    private Transaction depositTx;
    private Transaction payoutTx;

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
        when(payoutAddressEntry.getAddress()).thenReturn(BUYER_PAYOUT_ADDRESS);
        when(payoutAddressEntry.getAddressString()).thenReturn(BUYER_PAYOUT_ADDRESS.toString());
        when(btcWalletService.getAddressEntry(anyString(), eq(AddressEntry.Context.TRADE_PAYOUT)))
                .thenReturn(Optional.of(payoutAddressEntry));

        offer = make(a(OfferMaker.Offer,
                with(OfferMaker.direction, OfferDirection.BUY),
                with(OfferMaker.sellerSecurityDeposit, SELLER_SECURITY_DEPOSIT.value)));
        Coin buyerPayoutAmount = offer.getBuyerSecurityDeposit().add(TRADE_AMOUNT);
        depositTx = createDepositTx(buyerPayoutAmount.add(SELLER_SECURITY_DEPOSIT));
        depositTx.getConfidence().setAppearedAtChainHeight(100);
        // A payout that the wallet received in a filtered block has no witness data
        payoutTx = createPayoutTxWithoutWitness(depositTx, buyerPayoutAmount, SELLER_SECURITY_DEPOSIT);
        when(tradeWalletService.getWalletTx(payoutTx.getTxId())).thenReturn(payoutTx);
    }

    @AfterEach
    void tearDown() {
        UserThread.setTimerClass(FrameRateTimer.class);
        ManualTimer.clear();
    }

    @Test
    void payoutCompletesTheTradeWhenTheDepositTxIsOnlyInTheWalletAfterStartup() {
        Trade trade = trade();
        // The wallet was reset and has not yet synced the block with the deposit tx
        when(btcWalletService.getTransaction(depositTx.getTxId().toString())).thenReturn(null);

        new BuyerAsMakerProtocol((BuyerAsMakerTrade) trade).onInitialized();

        assertNull(trade.getErrorMessage());
        AddressConfidenceListener listener = capturedListener();
        assertEquals(BUYER_PAYOUT_ADDRESS, listener.getAddress());

        // The wallet syncs the block with the deposit tx, then the one with the payout
        when(btcWalletService.getTransaction(depositTx.getTxId().toString())).thenReturn(depositTx);
        listener.onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.BUILDING, payoutTx.getTxId()));

        assertSame(payoutTx, trade.getPayoutTx());
        assertEquals(Trade.State.BUYER_SAW_PAYOUT_TX_IN_NETWORK, trade.getTradeState());
        assertNull(trade.getErrorMessage());
    }

    // Without the deposit tx, a transaction to the payout address cannot be checked and does not count
    @Test
    void noPayoutCountsBeforeTheDepositTxIsInTheWallet() {
        Trade trade = trade();
        when(btcWalletService.getTransaction(depositTx.getTxId().toString())).thenReturn(null);

        new BuyerAsMakerProtocol((BuyerAsMakerTrade) trade).onInitialized();
        AddressConfidenceListener listener = capturedListener();
        listener.onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.BUILDING, payoutTx.getTxId()));

        assertNull(trade.getPayoutTx());
        assertEquals(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG, trade.getTradeState());
        verify(btcWalletService, never()).removeAddressConfidenceListener(any());

        // The next block changes the confidence of the payout again
        when(btcWalletService.getTransaction(depositTx.getTxId().toString())).thenReturn(depositTx);
        listener.onTransactionConfidenceChanged(
                confidence(TransactionConfidence.ConfidenceType.BUILDING, payoutTx.getTxId()));

        assertSame(payoutTx, trade.getPayoutTx());
        assertEquals(Trade.State.BUYER_SAW_PAYOUT_TX_IN_NETWORK, trade.getTradeState());
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

    // Buyer as maker in FIAT_SENT, as persisted: the deposit tx only by its id, to be read from the wallet
    private Trade trade() {
        ProcessModel processModel = new ProcessModel(offer.getId(), "account-id",
                new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic()));
        processModel.applyTransient(provider, mock(TradeManager.class), offer);
        Trade trade = new BuyerAsMakerTrade(offer,
                Coin.valueOf(1_000) /* txFee */,
                Coin.valueOf(100) /* takerFee */,
                true,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                btcWalletService,
                processModel,
                "uid");
        trade.setAmount(TRADE_AMOUNT);
        processModel.setMyMultiSigPubKey(BUYER_MULTI_SIG_KEY.getPubKey());
        TradingPeer tradingPeer = processModel.getTradePeer();
        tradingPeer.setMultiSigPubKey(SELLER_MULTI_SIG_KEY.getPubKey());
        tradingPeer.setPayoutAddressString(SELLER_PAYOUT_ADDRESS.toString());
        trade.setContract(contract(processModel.getPubKeyRing()));
        trade.setDepositTxId(depositTx.getTxId().toString());
        trade.setState(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
        return trade;
    }

    private Contract contract(PubKeyRing myPubKeyRing) {
        PubKeyRing peerPubKeyRing = new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
        return new Contract(offer.getOfferPayload().orElseThrow(),
                TRADE_AMOUNT.value,
                50_000_000L,
                "takerFeeTxId",
                new NodeAddress("buyer.onion:9999"),
                new NodeAddress("seller.onion:9999"),
                new NodeAddress("mediator.onion:9999"),
                true /* isBuyerMakerAndSellerTaker: we are the buyer and the maker */,
                "makerAccountId",
                "takerAccountId",
                null,
                null,
                myPubKeyRing,
                peerPubKeyRing,
                BUYER_PAYOUT_ADDRESS.toString(),
                SELLER_PAYOUT_ADDRESS.toString(),
                BUYER_MULTI_SIG_KEY.getPubKey(),
                SELLER_MULTI_SIG_KEY.getPubKey(),
                144,
                new NodeAddress("refund.onion:9999"),
                null,
                null,
                PaymentMethod.SEPA_ID,
                PaymentMethod.SEPA_ID,
                0);
    }

    private static Transaction createDepositTx(Coin depositOutputAmount) {
        Transaction depositTx = new Transaction(PARAMS);
        depositTx.addInput(new TransactionInput(PARAMS,
                depositTx,
                new byte[]{},
                new TransactionOutPoint(PARAMS, 0, Sha256Hash.of(new byte[]{7})),
                depositOutputAmount));
        depositTx.addOutput(depositOutputAmount, ScriptBuilder.createP2WSHOutputScript(REDEEM_SCRIPT));
        return depositTx;
    }

    private static Transaction createPayoutTxWithoutWitness(Transaction depositTx,
                                                            Coin buyerPayoutAmount,
                                                            Coin sellerPayoutAmount) {
        Transaction payoutTx = new Transaction(PARAMS);
        payoutTx.addInput(depositTx.getOutput(0));
        payoutTx.addOutput(buyerPayoutAmount, BUYER_PAYOUT_ADDRESS);
        payoutTx.addOutput(sellerPayoutAmount, SELLER_PAYOUT_ADDRESS);
        payoutTx.getInput(0).setScriptSig(ScriptBuilder.createEmpty());
        return payoutTx;
    }
}
